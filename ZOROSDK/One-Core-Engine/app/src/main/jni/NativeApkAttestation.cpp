#include "NativeApkAttestation.h"

#include <jni.h>
#include <obfuscate.h>

#include <algorithm>
#include <array>
#include <cctype>
#include <cerrno>
#include <cstdint>
#include <cstring>
#include <limits>
#include <string>
#include <vector>

#include <dlfcn.h>
#include <elf.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <unistd.h>

#include <openssl/crypto.h>
#include <openssl/evp.h>
#include <openssl/rsa.h>
#include <openssl/sha.h>
#include <openssl/x509.h>

#ifndef ONECORE_EXPECTED_SIGNING_SHA256
#define ONECORE_EXPECTED_SIGNING_SHA256 ""
#endif

namespace {
constexpr uint32_t ZIP_EOCD_MAGIC = 0x06054b50u;
constexpr uint32_t APK_V2_BLOCK_ID = 0x7109871au;
constexpr size_t APK_SIG_MAGIC_SIZE = 16u;
constexpr size_t EOCD_MIN_SIZE = 22u;
constexpr size_t EOCD_MAX_SEARCH = 22u + 65535u;
constexpr size_t APK_CHUNK_SIZE = 1024u * 1024u;
constexpr uint8_t APK_CHUNK_PREFIX = 0xA5u;
constexpr uint8_t APK_DIGEST_PREFIX = 0x5Au;
constexpr uint64_t MIN_APK_BYTES = 4096u;

constexpr uint32_t ALG_RSA_PSS_SHA256 = 0x0101u;
constexpr uint32_t ALG_RSA_PSS_SHA512 = 0x0102u;
constexpr uint32_t ALG_RSA_PKCS1_SHA256 = 0x0103u;
constexpr uint32_t ALG_RSA_PKCS1_SHA512 = 0x0104u;
constexpr uint32_t ALG_ECDSA_SHA256 = 0x0201u;
constexpr uint32_t ALG_ECDSA_SHA512 = 0x0202u;
constexpr uint32_t ALG_DSA_SHA256 = 0x0301u;

const unsigned char APK_SIG_MAGIC[APK_SIG_MAGIC_SIZE] = {
        'A','P','K',' ','S','i','g',' ','B','l','o','c','k',' ','4','2'
};

struct Slice {
    const uint8_t *data = nullptr;
    size_t size = 0;
};

struct SignatureRecord {
    uint32_t algorithm = 0;
    Slice bytes;
};

struct ApkLayout {
    int fd = -1;
    uint64_t file_size = 0;
    uint64_t signing_block_start = 0;
    uint64_t central_directory_offset = 0;
    uint64_t eocd_offset = 0;
};

uint16_t le16(const uint8_t *p) {
    return static_cast<uint16_t>(p[0])
            | (static_cast<uint16_t>(p[1]) << 8u);
}

uint32_t le32(const uint8_t *p) {
    return static_cast<uint32_t>(p[0])
            | (static_cast<uint32_t>(p[1]) << 8u)
            | (static_cast<uint32_t>(p[2]) << 16u)
            | (static_cast<uint32_t>(p[3]) << 24u);
}

uint64_t le64(const uint8_t *p) {
    uint64_t value = 0;
    for (int i = 7; i >= 0; --i) {
        value = (value << 8u) | p[i];
    }
    return value;
}

void put_le32(uint8_t *p, uint32_t value) {
    p[0] = static_cast<uint8_t>(value & 0xffu);
    p[1] = static_cast<uint8_t>((value >> 8u) & 0xffu);
    p[2] = static_cast<uint8_t>((value >> 16u) & 0xffu);
    p[3] = static_cast<uint8_t>((value >> 24u) & 0xffu);
}

bool read_fully(int fd, uint64_t offset, void *buffer, size_t length) {
    auto *out = static_cast<uint8_t *>(buffer);
    size_t done = 0;
    while (done < length) {
        ssize_t count = pread(fd, out + done, length - done,
                              static_cast<off_t>(offset + done));
        if (count < 0) {
            if (errno == EINTR) continue;
            return false;
        }
        if (count == 0) return false;
        done += static_cast<size_t>(count);
    }
    return true;
}

bool is_hex(char ch) {
    return std::isxdigit(static_cast<unsigned char>(ch)) != 0;
}

int hex_value(char ch) {
    if (ch >= '0' && ch <= '9') return ch - '0';
    ch = static_cast<char>(std::toupper(static_cast<unsigned char>(ch)));
    if (ch >= 'A' && ch <= 'F') return 10 + ch - 'A';
    return -1;
}

std::vector<std::array<uint8_t, SHA256_DIGEST_LENGTH>> compiled_allowed_digests() {
    std::vector<std::array<uint8_t, SHA256_DIGEST_LENGTH>> result;
    std::string config = OBFUSCATE(ONECORE_EXPECTED_SIGNING_SHA256);
    size_t cursor = 0;
    while (cursor <= config.size()) {
        size_t end = config.find_first_of(",;", cursor);
        if (end == std::string::npos) end = config.size();
        std::string item = config.substr(cursor, end - cursor);
        item.erase(std::remove_if(item.begin(), item.end(), [](char ch) {
            return ch == ':' || std::isspace(static_cast<unsigned char>(ch));
        }), item.end());
        if (!item.empty()) {
            if (item.size() != SHA256_DIGEST_LENGTH * 2u
                    || !std::all_of(item.begin(), item.end(), is_hex)) {
                result.clear();
                return result;
            }
            std::array<uint8_t, SHA256_DIGEST_LENGTH> digest{};
            for (size_t i = 0; i < digest.size(); ++i) {
                int high = hex_value(item[i * 2u]);
                int low = hex_value(item[i * 2u + 1u]);
                if (high < 0 || low < 0) {
                    result.clear();
                    return result;
                }
                digest[i] = static_cast<uint8_t>((high << 4) | low);
            }
            result.push_back(digest);
        }
        if (end == config.size()) break;
        cursor = end + 1u;
    }
    std::fill(config.begin(), config.end(), '\0');
    return result;
}

bool digest_is_allowed(const uint8_t digest[SHA256_DIGEST_LENGTH]) {
    const auto allowed = compiled_allowed_digests();
    if (allowed.empty()) return false;
    int matched = 0;
    for (const auto &candidate : allowed) {
        matched |= CRYPTO_memcmp(digest, candidate.data(), candidate.size()) == 0;
    }
    return matched != 0;
}

bool next_length_prefixed(const uint8_t *&cursor, const uint8_t *end, Slice &slice) {
    if (cursor == nullptr || end == nullptr || cursor > end
            || static_cast<size_t>(end - cursor) < sizeof(uint32_t)) {
        return false;
    }
    uint32_t length = le32(cursor);
    cursor += sizeof(uint32_t);
    if (static_cast<uint64_t>(length) > static_cast<uint64_t>(end - cursor)) {
        return false;
    }
    slice.data = cursor;
    slice.size = length;
    cursor += length;
    return true;
}

bool parse_records(Slice sequence, std::vector<SignatureRecord> &records) {
    const uint8_t *cursor = sequence.data;
    const uint8_t *end = sequence.data + sequence.size;
    while (cursor < end) {
        Slice record;
        if (!next_length_prefixed(cursor, end, record) || record.size < 8u) return false;
        const uint8_t *record_cursor = record.data;
        const uint8_t *record_end = record.data + record.size;
        uint32_t algorithm = le32(record_cursor);
        record_cursor += 4u;
        Slice bytes;
        if (!next_length_prefixed(record_cursor, record_end, bytes)
                || record_cursor != record_end || bytes.size == 0u) {
            return false;
        }
        records.push_back({algorithm, bytes});
    }
    return cursor == end && !records.empty();
}

const EVP_MD *digest_for_algorithm(uint32_t algorithm) {
    switch (algorithm) {
        case ALG_RSA_PSS_SHA256:
        case ALG_RSA_PKCS1_SHA256:
        case ALG_ECDSA_SHA256:
        case ALG_DSA_SHA256:
            return EVP_sha256();
        case ALG_RSA_PSS_SHA512:
        case ALG_RSA_PKCS1_SHA512:
        case ALG_ECDSA_SHA512:
            return EVP_sha512();
        default:
            return nullptr;
    }
}

bool is_rsa_pss(uint32_t algorithm) {
    return algorithm == ALG_RSA_PSS_SHA256 || algorithm == ALG_RSA_PSS_SHA512;
}

bool is_rsa_pkcs1(uint32_t algorithm) {
    return algorithm == ALG_RSA_PKCS1_SHA256 || algorithm == ALG_RSA_PKCS1_SHA512;
}

bool verify_signed_data_signature(
        uint32_t algorithm,
        Slice public_key,
        Slice signed_data,
        Slice signature) {
    const EVP_MD *md = digest_for_algorithm(algorithm);
    if (md == nullptr || public_key.size == 0u || signed_data.size == 0u || signature.size == 0u) {
        return false;
    }

    const unsigned char *key_cursor = public_key.data;
    EVP_PKEY *key = d2i_PUBKEY(nullptr, &key_cursor, static_cast<long>(public_key.size));
    if (key == nullptr || key_cursor != public_key.data + public_key.size) {
        if (key != nullptr) EVP_PKEY_free(key);
        return false;
    }

    EVP_MD_CTX *ctx = EVP_MD_CTX_new();
    EVP_PKEY_CTX *pkey_ctx = nullptr;
    bool ok = ctx != nullptr
            && EVP_DigestVerifyInit(ctx, &pkey_ctx, md, nullptr, key) == 1;
    if (ok && is_rsa_pss(algorithm)) {
        const int digest_size = EVP_MD_size(md);
        ok = pkey_ctx != nullptr
                && EVP_PKEY_CTX_set_rsa_padding(pkey_ctx, RSA_PKCS1_PSS_PADDING) > 0
                && EVP_PKEY_CTX_set_rsa_mgf1_md(pkey_ctx, md) > 0
                && EVP_PKEY_CTX_set_rsa_pss_saltlen(pkey_ctx, digest_size) > 0;
    } else if (ok && is_rsa_pkcs1(algorithm)) {
        ok = pkey_ctx != nullptr
                && EVP_PKEY_CTX_set_rsa_padding(pkey_ctx, RSA_PKCS1_PADDING) > 0;
    }

    if (ok) {
        ok = EVP_DigestVerifyUpdate(ctx, signed_data.data, signed_data.size) == 1
                && EVP_DigestVerifyFinal(ctx, signature.data, signature.size) == 1;
    }

    if (ctx != nullptr) EVP_MD_CTX_free(ctx);
    EVP_PKEY_free(key);
    return ok;
}

bool certificate_matches_public_key(Slice certificate, Slice public_key) {
    const unsigned char *cert_cursor = certificate.data;
    X509 *x509 = d2i_X509(nullptr, &cert_cursor, static_cast<long>(certificate.size));
    if (x509 == nullptr || cert_cursor != certificate.data + certificate.size) {
        if (x509 != nullptr) X509_free(x509);
        return false;
    }
    EVP_PKEY *cert_key = X509_get_pubkey(x509);
    if (cert_key == nullptr) {
        X509_free(x509);
        return false;
    }
    int encoded_length = i2d_PUBKEY(cert_key, nullptr);
    std::vector<uint8_t> encoded;
    bool ok = encoded_length > 0;
    if (ok) {
        encoded.resize(static_cast<size_t>(encoded_length));
        unsigned char *write_cursor = encoded.data();
        ok = i2d_PUBKEY(cert_key, &write_cursor) == encoded_length
                && encoded.size() == public_key.size
                && CRYPTO_memcmp(encoded.data(), public_key.data, public_key.size) == 0;
    }
    if (!encoded.empty()) OPENSSL_cleanse(encoded.data(), encoded.size());
    EVP_PKEY_free(cert_key);
    X509_free(x509);
    return ok;
}

bool signer_certificate_allowed(Slice certificate) {
    uint8_t digest[SHA256_DIGEST_LENGTH];
    if (certificate.size == 0u
            || SHA256(certificate.data, certificate.size, digest) == nullptr) {
        return false;
    }
    bool allowed = digest_is_allowed(digest);
    OPENSSL_cleanse(digest, sizeof(digest));
    return allowed;
}

uint64_t chunk_count(uint64_t length) {
    return length == 0u ? 0u : ((length - 1u) / APK_CHUNK_SIZE) + 1u;
}

bool digest_one_chunk(
        const EVP_MD *md,
        const uint8_t *data,
        size_t length,
        std::vector<uint8_t> &digest) {
    if (md == nullptr || length > APK_CHUNK_SIZE
            || length > static_cast<size_t>(std::numeric_limits<uint32_t>::max())) {
        return false;
    }
    uint8_t prefix[5];
    prefix[0] = APK_CHUNK_PREFIX;
    put_le32(prefix + 1u, static_cast<uint32_t>(length));

    EVP_MD_CTX *ctx = EVP_MD_CTX_new();
    if (ctx == nullptr) return false;
    unsigned int output_length = 0;
    digest.resize(static_cast<size_t>(EVP_MD_size(md)));
    bool ok = EVP_DigestInit_ex(ctx, md, nullptr) == 1
            && EVP_DigestUpdate(ctx, prefix, sizeof(prefix)) == 1
            && (length == 0u || EVP_DigestUpdate(ctx, data, length) == 1)
            && EVP_DigestFinal_ex(ctx, digest.data(), &output_length) == 1
            && output_length == digest.size();
    EVP_MD_CTX_free(ctx);
    return ok;
}

bool append_file_section_chunks(
        EVP_MD_CTX *final_ctx,
        const EVP_MD *md,
        int fd,
        uint64_t offset,
        uint64_t length) {
    std::vector<uint8_t> buffer(APK_CHUNK_SIZE);
    std::vector<uint8_t> digest;
    uint64_t remaining = length;
    uint64_t cursor = offset;
    while (remaining > 0u) {
        size_t amount = static_cast<size_t>(std::min<uint64_t>(remaining, APK_CHUNK_SIZE));
        if (!read_fully(fd, cursor, buffer.data(), amount)
                || !digest_one_chunk(md, buffer.data(), amount, digest)
                || EVP_DigestUpdate(final_ctx, digest.data(), digest.size()) != 1) {
            return false;
        }
        cursor += amount;
        remaining -= amount;
    }
    if (!buffer.empty()) OPENSSL_cleanse(buffer.data(), buffer.size());
    if (!digest.empty()) OPENSSL_cleanse(digest.data(), digest.size());
    return true;
}

bool append_memory_section_chunks(
        EVP_MD_CTX *final_ctx,
        const EVP_MD *md,
        const uint8_t *data,
        size_t length) {
    std::vector<uint8_t> digest;
    size_t cursor = 0;
    while (cursor < length) {
        size_t amount = std::min<size_t>(length - cursor, APK_CHUNK_SIZE);
        if (!digest_one_chunk(md, data + cursor, amount, digest)
                || EVP_DigestUpdate(final_ctx, digest.data(), digest.size()) != 1) {
            return false;
        }
        cursor += amount;
    }
    if (!digest.empty()) OPENSSL_cleanse(digest.data(), digest.size());
    return true;
}

bool compute_apk_content_digest(
        const ApkLayout &layout,
        const EVP_MD *md,
        std::vector<uint8_t> &output) {
    if (layout.fd < 0 || md == nullptr
            || layout.signing_block_start > layout.central_directory_offset
            || layout.central_directory_offset > layout.eocd_offset
            || layout.eocd_offset > layout.file_size
            || layout.signing_block_start > std::numeric_limits<uint32_t>::max()) {
        return false;
    }

    const uint64_t section1 = layout.signing_block_start;
    const uint64_t section2 = layout.eocd_offset - layout.central_directory_offset;
    const uint64_t section3 = layout.file_size - layout.eocd_offset;
    const uint64_t count64 = chunk_count(section1) + chunk_count(section2) + chunk_count(section3);
    if (count64 == 0u || count64 > std::numeric_limits<uint32_t>::max()) return false;

    std::vector<uint8_t> eocd(static_cast<size_t>(section3));
    if (!read_fully(layout.fd, layout.eocd_offset, eocd.data(), eocd.size())
            || eocd.size() < EOCD_MIN_SIZE || le32(eocd.data()) != ZIP_EOCD_MAGIC) {
        return false;
    }
    put_le32(eocd.data() + 16u, static_cast<uint32_t>(layout.signing_block_start));

    uint8_t final_prefix[5];
    final_prefix[0] = APK_DIGEST_PREFIX;
    put_le32(final_prefix + 1u, static_cast<uint32_t>(count64));

    EVP_MD_CTX *ctx = EVP_MD_CTX_new();
    if (ctx == nullptr) return false;
    bool ok = EVP_DigestInit_ex(ctx, md, nullptr) == 1
            && EVP_DigestUpdate(ctx, final_prefix, sizeof(final_prefix)) == 1
            && append_file_section_chunks(ctx, md, layout.fd, 0u, section1)
            && append_file_section_chunks(
                    ctx, md, layout.fd, layout.central_directory_offset, section2)
            && append_memory_section_chunks(ctx, md, eocd.data(), eocd.size());

    unsigned int digest_length = 0;
    output.resize(static_cast<size_t>(EVP_MD_size(md)));
    if (ok) {
        ok = EVP_DigestFinal_ex(ctx, output.data(), &digest_length) == 1
                && digest_length == output.size();
    }
    EVP_MD_CTX_free(ctx);
    if (!eocd.empty()) OPENSSL_cleanse(eocd.data(), eocd.size());
    return ok;
}

const SignatureRecord *find_record(
        const std::vector<SignatureRecord> &records,
        uint32_t algorithm) {
    for (const auto &record : records) {
        if (record.algorithm == algorithm) return &record;
    }
    return nullptr;
}

bool verify_signer(Slice signer, const ApkLayout &layout) {
    const uint8_t *cursor = signer.data;
    const uint8_t *end = signer.data + signer.size;
    Slice signed_data;
    Slice signatures;
    Slice public_key;
    if (!next_length_prefixed(cursor, end, signed_data)
            || !next_length_prefixed(cursor, end, signatures)
            || !next_length_prefixed(cursor, end, public_key)
            || cursor != end || signed_data.size == 0u || public_key.size == 0u) {
        return false;
    }

    const uint8_t *signed_cursor = signed_data.data;
    const uint8_t *signed_end = signed_data.data + signed_data.size;
    Slice digests;
    Slice certificates;
    Slice attributes;
    if (!next_length_prefixed(signed_cursor, signed_end, digests)
            || !next_length_prefixed(signed_cursor, signed_end, certificates)
            || !next_length_prefixed(signed_cursor, signed_end, attributes)
            || signed_cursor != signed_end) {
        return false;
    }

    const uint8_t *cert_cursor = certificates.data;
    const uint8_t *cert_end = certificates.data + certificates.size;
    Slice signer_certificate;
    if (!next_length_prefixed(cert_cursor, cert_end, signer_certificate)
            || signer_certificate.size == 0u
            || !signer_certificate_allowed(signer_certificate)
            || !certificate_matches_public_key(signer_certificate, public_key)) {
        return false;
    }

    std::vector<SignatureRecord> digest_records;
    std::vector<SignatureRecord> signature_records;
    if (!parse_records(digests, digest_records)
            || !parse_records(signatures, signature_records)) {
        return false;
    }

    // Accept only when one supported signature algorithm verifies the signed-data signature AND
    // the corresponding APK content digest independently recomputes to the exact signed value.
    for (const auto &signature_record : signature_records) {
        const EVP_MD *md = digest_for_algorithm(signature_record.algorithm);
        if (md == nullptr) continue;
        const SignatureRecord *digest_record = find_record(digest_records, signature_record.algorithm);
        if (digest_record == nullptr) continue;
        if (!verify_signed_data_signature(
                signature_record.algorithm,
                public_key,
                signed_data,
                signature_record.bytes)) {
            continue;
        }
        std::vector<uint8_t> actual_digest;
        if (!compute_apk_content_digest(layout, md, actual_digest)) continue;
        const bool matches = actual_digest.size() == digest_record->bytes.size
                && CRYPTO_memcmp(
                        actual_digest.data(),
                        digest_record->bytes.data,
                        actual_digest.size()) == 0;
        if (!actual_digest.empty()) OPENSSL_cleanse(actual_digest.data(), actual_digest.size());
        if (matches) return true;
    }
    return false;
}

bool locate_eocd(int fd, uint64_t file_size, uint64_t &eocd_offset, uint64_t &cd_offset) {
    const size_t search = static_cast<size_t>(std::min<uint64_t>(file_size, EOCD_MAX_SEARCH));
    if (search < EOCD_MIN_SIZE) return false;
    std::vector<uint8_t> tail(search);
    const uint64_t tail_offset = file_size - search;
    if (!read_fully(fd, tail_offset, tail.data(), tail.size())) return false;

    for (size_t index = search - EOCD_MIN_SIZE + 1u; index-- > 0u;) {
        if (le32(tail.data() + index) != ZIP_EOCD_MAGIC) continue;
        uint16_t comment_length = le16(tail.data() + index + 20u);
        if (index + EOCD_MIN_SIZE + comment_length != search) continue;
        uint32_t central_offset = le32(tail.data() + index + 16u);
        uint32_t central_size = le32(tail.data() + index + 12u);
        if (central_offset == 0xffffffffu || central_size == 0xffffffffu) return false;
        const uint64_t absolute_eocd = tail_offset + index;
        if (static_cast<uint64_t>(central_offset) + central_size != absolute_eocd) return false;
        eocd_offset = absolute_eocd;
        cd_offset = central_offset;
        return true;
    }
    return false;
}

bool locate_v2_block(
        int fd,
        uint64_t file_size,
        ApkLayout &layout,
        std::vector<uint8_t> &v2_value) {
    uint64_t eocd_offset = 0;
    uint64_t cd_offset = 0;
    if (!locate_eocd(fd, file_size, eocd_offset, cd_offset) || cd_offset < 32u) return false;

    uint8_t footer[24];
    if (!read_fully(fd, cd_offset - sizeof(footer), footer, sizeof(footer))
            || CRYPTO_memcmp(footer + 8u, APK_SIG_MAGIC, APK_SIG_MAGIC_SIZE) != 0) {
        return false;
    }
    uint64_t size_in_footer = le64(footer);
    if (size_in_footer < 24u || size_in_footer > cd_offset - 8u) return false;
    uint64_t total_block_size = size_in_footer + 8u;
    uint64_t block_start = cd_offset - total_block_size;

    uint8_t header_size_bytes[8];
    if (!read_fully(fd, block_start, header_size_bytes, sizeof(header_size_bytes))
            || le64(header_size_bytes) != size_in_footer) {
        return false;
    }

    const uint64_t entries_start = block_start + 8u;
    const uint64_t entries_end = cd_offset - 24u;
    if (entries_start >= entries_end) return false;

    uint64_t cursor = entries_start;
    bool found = false;
    while (cursor < entries_end) {
        uint8_t length_bytes[8];
        if (!read_fully(fd, cursor, length_bytes, sizeof(length_bytes))) return false;
        uint64_t entry_length = le64(length_bytes);
        cursor += 8u;
        if (entry_length < 4u || entry_length > entries_end - cursor) return false;
        uint8_t id_bytes[4];
        if (!read_fully(fd, cursor, id_bytes, sizeof(id_bytes))) return false;
        uint32_t id = le32(id_bytes);
        if (id == APK_V2_BLOCK_ID) {
            if (found) return false;
            const uint64_t value_length = entry_length - 4u;
            if (value_length == 0u || value_length > 4u * 1024u * 1024u) return false;
            v2_value.resize(static_cast<size_t>(value_length));
            if (!read_fully(fd, cursor + 4u, v2_value.data(), v2_value.size())) return false;
            found = true;
        }
        cursor += entry_length;
    }
    if (!found || cursor != entries_end) return false;

    layout.fd = fd;
    layout.file_size = file_size;
    layout.signing_block_start = block_start;
    layout.central_directory_offset = cd_offset;
    layout.eocd_offset = eocd_offset;
    return true;
}

bool verify_v2_signers(const std::vector<uint8_t> &v2_value, const ApkLayout &layout) {
    if (v2_value.empty()) return false;
    const uint8_t *cursor = v2_value.data();
    const uint8_t *end = v2_value.data() + v2_value.size();
    size_t signer_count = 0u;
    while (cursor < end) {
        Slice signer;
        if (!next_length_prefixed(cursor, end, signer) || signer.size == 0u) return false;
        if (!verify_signer(signer, layout)) return false;
        ++signer_count;
        if (signer_count > 8u) return false;
    }
    return cursor == end && signer_count > 0u;
}

bool hash_fd_region(int fd, uint64_t offset, uint64_t length, uint8_t out[SHA256_DIGEST_LENGTH]) {
    SHA256_CTX ctx;
    if (SHA256_Init(&ctx) != 1) return false;
    std::vector<uint8_t> buffer(64u * 1024u);
    uint64_t remaining = length;
    uint64_t cursor = offset;
    while (remaining > 0u) {
        size_t amount = static_cast<size_t>(std::min<uint64_t>(remaining, buffer.size()));
        if (!read_fully(fd, cursor, buffer.data(), amount)
                || SHA256_Update(&ctx, buffer.data(), amount) != 1) {
            OPENSSL_cleanse(buffer.data(), buffer.size());
            return false;
        }
        cursor += amount;
        remaining -= amount;
    }
    OPENSSL_cleanse(buffer.data(), buffer.size());
    return SHA256_Final(out, &ctx) == 1;
}

bool hash_memory_region(
        const uint8_t *memory,
        size_t length,
        uint8_t out[SHA256_DIGEST_LENGTH]) {
    return memory != nullptr && length > 0u
            && SHA256(memory, length, out) != nullptr;
}

} // namespace

bool onecore_verify_native_text_integrity() {
    Dl_info info{};
    if (dladdr(reinterpret_cast<void *>(&onecore_verify_native_text_integrity), &info) == 0
            || info.dli_fname == nullptr || info.dli_fbase == nullptr) {
        return false;
    }
    std::string path(info.dli_fname);
    if (path.find("libKESHAVXOWNERLoader.so") == std::string::npos || path.find('!') != std::string::npos) {
        return false;
    }

    int fd = open(path.c_str(), O_RDONLY | O_CLOEXEC | O_NOFOLLOW);
    if (fd < 0) return false;
    Elf64_Ehdr header{};
    bool ok = read_fully(fd, 0u, &header, sizeof(header))
            && std::memcmp(header.e_ident, ELFMAG, SELFMAG) == 0
            && header.e_ident[EI_CLASS] == ELFCLASS64
            && header.e_machine == EM_AARCH64
            && header.e_phentsize == sizeof(Elf64_Phdr)
            && header.e_phnum > 0u && header.e_phnum < 128u;
    if (!ok) {
        close(fd);
        return false;
    }

    std::vector<Elf64_Phdr> phdrs(header.e_phnum);
    ok = read_fully(fd, header.e_phoff, phdrs.data(), phdrs.size() * sizeof(Elf64_Phdr));
    bool verified_segment = false;
    const auto *base = static_cast<const uint8_t *>(info.dli_fbase);
    for (const auto &phdr : phdrs) {
        if (!ok) break;
        if (phdr.p_type != PT_LOAD || (phdr.p_flags & PF_X) == 0 || phdr.p_filesz == 0u) continue;
        if (phdr.p_filesz > 64u * 1024u * 1024u) {
            ok = false;
            break;
        }
        uint8_t disk_digest[SHA256_DIGEST_LENGTH];
        uint8_t memory_digest[SHA256_DIGEST_LENGTH];
        const uint8_t *mapped = base + phdr.p_vaddr;
        ok = hash_fd_region(fd, phdr.p_offset, phdr.p_filesz, disk_digest)
                && hash_memory_region(mapped, static_cast<size_t>(phdr.p_filesz), memory_digest)
                && CRYPTO_memcmp(disk_digest, memory_digest, SHA256_DIGEST_LENGTH) == 0;
        OPENSSL_cleanse(disk_digest, sizeof(disk_digest));
        OPENSSL_cleanse(memory_digest, sizeof(memory_digest));
        if (!ok) break;
        verified_segment = true;
    }
    close(fd);
    return ok && verified_segment;
}

bool onecore_verify_installed_apk(const char *apk_path) {
    if (apk_path == nullptr || apk_path[0] != '/' || compiled_allowed_digests().empty()) return false;
    std::string path(apk_path);
    if (path.size() < 5u || path.rfind(".apk") != path.size() - 4u) return false;

    struct stat link_stat{};
    if (lstat(path.c_str(), &link_stat) != 0 || S_ISLNK(link_stat.st_mode)) return false;

    int fd = open(path.c_str(), O_RDONLY | O_CLOEXEC | O_NOFOLLOW);
    if (fd < 0) return false;
    struct stat st{};
    bool ok = fstat(fd, &st) == 0
            && S_ISREG(st.st_mode)
            && st.st_size >= static_cast<off_t>(MIN_APK_BYTES)
            && (st.st_mode & S_IWOTH) == 0;
    if (!ok) {
        close(fd);
        return false;
    }

    ApkLayout layout;
    std::vector<uint8_t> v2_value;
    ok = locate_v2_block(fd, static_cast<uint64_t>(st.st_size), layout, v2_value)
            && verify_v2_signers(v2_value, layout)
            && onecore_verify_native_text_integrity();
    if (!v2_value.empty()) OPENSSL_cleanse(v2_value.data(), v2_value.size());
    close(fd);
    return ok;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_onecore_loader_security_NativeSigningVerifier_verifyInstalledApkNative(
        JNIEnv *env,
        jclass,
        jstring apkPath,
        jstring actualPackage) {
    if (apkPath == nullptr || actualPackage == nullptr) return JNI_FALSE;
    const char *package_value = env->GetStringUTFChars(actualPackage, nullptr);
    const char *path_value = env->GetStringUTFChars(apkPath, nullptr);
    if (package_value == nullptr || path_value == nullptr) {
        if (package_value != nullptr) env->ReleaseStringUTFChars(actualPackage, package_value);
        if (path_value != nullptr) env->ReleaseStringUTFChars(apkPath, path_value);
        return JNI_FALSE;
    }
    const bool package_ok = std::strcmp(package_value, OBFUSCATE("com.onecore.loader")) == 0;
    const bool apk_ok = package_ok && onecore_verify_installed_apk(path_value);
    env->ReleaseStringUTFChars(actualPackage, package_value);
    env->ReleaseStringUTFChars(apkPath, path_value);
    return apk_ok ? JNI_TRUE : JNI_FALSE;
}
