//
//  KittyMemory.cpp
//
//  Created by MJ (Ruit) on 1/1/19.
//

#include "KittyMemory.h"

using KittyMemory::Memory_Status;
using KittyMemory::ProcMap;


struct mapsCache {
    std::string identifier;
    ProcMap map;
};

static std::vector<mapsCache> __mapsCache;
static ProcMap findMapInCache(std::string id){
    ProcMap ret;
    for(int i = 0; i < __mapsCache.size(); i++){
        if(__mapsCache[i].identifier.compare(id) == 0){
            ret = __mapsCache[i].map;
            break;
        }
    }
    return ret;
}


bool KittyMemory::ProtectAddr(void *addr, size_t length, int protection) {
   uintptr_t pageStart = _PAGE_START_OF_(addr);
   uintptr_t pageLen   = _PAGE_LEN_OF_(addr, length);
   return (
     mprotect(reinterpret_cast<void *>(pageStart), pageLen, protection) != -1
 );
}


Memory_Status KittyMemory::memWrite(void *addr, const void *buffer, size_t len) {
    if (addr == NULL)
        return INV_ADDR;

    if (buffer == NULL)
        return INV_BUF;

    if (len < 1 || len > INT_MAX)
        return INV_LEN;

    if (!ProtectAddr(addr, len, _PROT_RWX_))
        return INV_PROT;

    if (memcpy(addr, buffer, len) != NULL && ProtectAddr(addr, len, _PROT_RX_))
        return SUCCESS;

    return FAILED;
}


Memory_Status KittyMemory::memRead(void *buffer, const void *addr, size_t len) {
    if (addr == NULL)
        return INV_ADDR;

    if (buffer == NULL)
        return INV_BUF;

    if (len < 1 || len > INT_MAX)
        return INV_LEN;

    if (memcpy(buffer, addr, len) != NULL)
        return SUCCESS;

    return FAILED;
}


std::string KittyMemory::read2HexStr(const void *addr, size_t len) {
    if (addr == nullptr || len == 0 || len > INT_MAX / 2)
        return {};

    std::vector<unsigned char> bytes(len, 0);
    if (memRead(bytes.data(), addr, len) != SUCCESS)
        return {};

    static constexpr char HEX[] = "0123456789ABCDEF";
    std::string result(len * 2, '0');
    for (size_t i = 0; i < len; ++i) {
        result[i * 2] = HEX[bytes[i] >> 4];
        result[i * 2 + 1] = HEX[bytes[i] & 0x0F];
    }
    return result;
}

ProcMap KittyMemory::getLibraryMap(const char *libraryName) {
    ProcMap retMap;
    char line[512] = {0};

    FILE *fp = fopen("/proc/self/maps", "rt");
    if (fp != NULL) {
        while (fgets(line, sizeof(line), fp)) {
            if (strstr(line, libraryName)) {
                char tmpPerms[5] = {0}, tmpDev[12] = {0}, tmpPathname[444] = {0};
                // parse a line in maps file
                // (format) startAddress-endAddress perms offset dev inode pathname
                sscanf(line, "%llx-%llx %s %ld %s %d %s",
                       (long long unsigned *) &retMap.startAddr,
                       (long long unsigned *) &retMap.endAddr,
                       tmpPerms, &retMap.offset, tmpDev, &retMap.inode, tmpPathname);

                retMap.length = (uintptr_t) retMap.endAddr - (uintptr_t) retMap.startAddr;
                retMap.perms = tmpPerms;
                retMap.dev = tmpDev;
                retMap.pathname = tmpPathname;

                break;
            }
        }
        fclose(fp);
    }
    return retMap;
}

uintptr_t KittyMemory::getAbsoluteAddress(const char *libraryName, uintptr_t relativeAddr, bool useCache) {
    ProcMap libMap;

    if(useCache){
        libMap = findMapInCache(libraryName);
        if(libMap.isValid())
        return (reinterpret_cast<uintptr_t>(libMap.startAddr) + relativeAddr);
    }
       
    libMap = getLibraryMap(libraryName);
    if (!libMap.isValid())
        return 0;

    if(useCache){
        mapsCache cachedMap;
        cachedMap.identifier = libraryName;
        cachedMap.map        = libMap;
        __mapsCache.push_back(cachedMap);
    }

    return (reinterpret_cast<uintptr_t>(libMap.startAddr) + relativeAddr);
}
