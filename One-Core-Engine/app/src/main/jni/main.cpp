#include <jni.h>
#include <string>
#include <vector>
#include <cstring>
#include <obfuscate.h>
#include <sys/stat.h>
#include <openssl/crypto.h>
#include <openssl/sha.h>
#include "backends/ModsLoader.h"
#include "ESP.h"
#include "Hacks.h"

ESP espOverlay;
int type = 1, utype = 2;

//stcitc std::string EXP = "NULL";

extern "C"
JNIEXPORT jstring JNICALL
com_onecore_loader_activity_LoginActivity_GetKey(JNIEnv *env, jclass clazz) {
    return env->NewStringUTF(OBFUSCATE("https://t.me/ParallaxOwner")); //telegram link
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_onecore_loader_security_NativeSigningVerifier_verifySigningIdentity(
        JNIEnv *env,
        jclass,
        jobjectArray allowedDigests,
        jobjectArray certificates,
        jstring actualPackage,
        jstring expectedPackage) {
    if (allowedDigests == nullptr || certificates == nullptr
            || actualPackage == nullptr || expectedPackage == nullptr) {
        return JNI_FALSE;
    }

    const char *actual = env->GetStringUTFChars(actualPackage, nullptr);
    const char *expected = env->GetStringUTFChars(expectedPackage, nullptr);
    if (actual == nullptr || expected == nullptr) {
        if (actual != nullptr) env->ReleaseStringUTFChars(actualPackage, actual);
        if (expected != nullptr) env->ReleaseStringUTFChars(expectedPackage, expected);
        return JNI_FALSE;
    }
    const bool packageMatches = std::strcmp(actual, expected) == 0;
    env->ReleaseStringUTFChars(actualPackage, actual);
    env->ReleaseStringUTFChars(expectedPackage, expected);
    if (!packageMatches) {
        return JNI_FALSE;
    }

    const jsize allowedCount = env->GetArrayLength(allowedDigests);
    const jsize certificateCount = env->GetArrayLength(certificates);
    if (allowedCount <= 0 || certificateCount <= 0) {
        return JNI_FALSE;
    }

    std::vector<std::vector<unsigned char>> allowed;
    allowed.reserve(static_cast<size_t>(allowedCount));
    for (jsize index = 0; index < allowedCount; ++index) {
        auto digestArray = static_cast<jbyteArray>(
                env->GetObjectArrayElement(allowedDigests, index));
        if (digestArray == nullptr || env->GetArrayLength(digestArray) != SHA256_DIGEST_LENGTH) {
            if (digestArray != nullptr) env->DeleteLocalRef(digestArray);
            return JNI_FALSE;
        }
        std::vector<unsigned char> digest(SHA256_DIGEST_LENGTH);
        env->GetByteArrayRegion(
                digestArray,
                0,
                SHA256_DIGEST_LENGTH,
                reinterpret_cast<jbyte *>(digest.data()));
        env->DeleteLocalRef(digestArray);
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            return JNI_FALSE;
        }
        allowed.push_back(std::move(digest));
    }

    for (jsize index = 0; index < certificateCount; ++index) {
        auto certificateArray = static_cast<jbyteArray>(
                env->GetObjectArrayElement(certificates, index));
        if (certificateArray == nullptr) {
            return JNI_FALSE;
        }
        const jsize certificateLength = env->GetArrayLength(certificateArray);
        if (certificateLength <= 0) {
            env->DeleteLocalRef(certificateArray);
            return JNI_FALSE;
        }

        std::vector<unsigned char> certificate(static_cast<size_t>(certificateLength));
        env->GetByteArrayRegion(
                certificateArray,
                0,
                certificateLength,
                reinterpret_cast<jbyte *>(certificate.data()));
        env->DeleteLocalRef(certificateArray);
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            return JNI_FALSE;
        }

        unsigned char actualDigest[SHA256_DIGEST_LENGTH];
        if (SHA256(certificate.data(), certificate.size(), actualDigest) == nullptr) {
            return JNI_FALSE;
        }

        bool signerAllowed = false;
        for (const auto &allowedDigest : allowed) {
            signerAllowed |= CRYPTO_memcmp(
                    actualDigest,
                    allowedDigest.data(),
                    SHA256_DIGEST_LENGTH) == 0;
        }
        OPENSSL_cleanse(actualDigest, sizeof(actualDigest));
        OPENSSL_cleanse(certificate.data(), certificate.size());
        if (!signerAllowed) {
            return JNI_FALSE;
        }
    }

    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_onecore_loader_floating_Overlay_DrawOn(JNIEnv * env, jclass, jobject espView, jobject canvas) {
	espOverlay = ESP(env, espView, canvas);
	if (espOverlay.isValid()) {
		DrawESP(espOverlay, espOverlay.getWidth(), espOverlay.getHeight());
	}
}

extern "C" JNIEXPORT void JNICALL 
Java_com_onecore_loader_floating_Overlay_Close(JNIEnv *, jobject) {
	Close();
}

extern "C" JNIEXPORT void JNICALL
Java_com_onecore_loader_floating_FloatLogo_SettingValue(JNIEnv *, jobject, jint code, jboolean jboolean1) {
	switch ((int)code) {
	case 2:
		isPlayerTeamID = jboolean1;
		break;
	case 3:
		isPlayerDist = jboolean1;
		break;
	case 4:
		isPlayerHealth = jboolean1;
		break;
	case 5:
		isPlayerName = jboolean1;
		break;
	case 6:
		isPlayerHead = jboolean1;
		break;
	case 7:
		isr360Alert = jboolean1;
		break;
	case 8:
		isSkelton = jboolean1;
		break;
	case 9:
		isGrenadeWarning = jboolean1;
		break;
	case 10:
		isEnemyWeapon = jboolean1;
		break;
	case 11:
		if (jboolean1 != 0)
			options.openState = 0;
		else
			options.openState = -1;
		break;
	case 12:
		options.tracingStatus = jboolean1;
		break;
	case 13:
		options.pour = jboolean1;
		break;
	case 14:
		options.isMetroMode = jboolean1;
		break;
	case 15:
		options.isRadar = jboolean1;
		break;
	}
}
/*
extern "C" JNIEXPORT void JNICALL
Java_com_onecore_loader_floating_FloatLogo_SettingMemory(JNIEnv *, jobject, jint code, jboolean is_true) {
	switch ((int)code) {
	case 1:
		request.memory.LessRecoil = is_true;
		break;
	case 2:
		request.memory.InstantHit = is_true;
		break;
	case 4:
		request.memory.SmallCrosshair = is_true;
		break;
	case 5:
		request.memory.ZeroRecoil = is_true;
		break;
	case 6:
		request.memory.FastParachute = is_true;
		break;
	case 7:
		request.memory.NoShake = is_true;
		break;
	case 8:
		request.memory.HitEffect = is_true;
		break;
	}
}
*/
extern "C" JNIEXPORT void JNICALL
Java_com_onecore_loader_floating_FloatLogo_SettingValueI(JNIEnv *, jobject, jint code, jint number) {
	switch ((int)code) {
	case 1:
		isPlayerBox = number;
		break;
	case 2:
		isPlayerLine = number;
		break;
	}
}

extern "C" JNIEXPORT void JNICALL
Java_com_onecore_loader_floating_FloatAim_AimbotFOV(JNIEnv *, jclass, jboolean isTrue)
{
	if (isTrue)
		options.openState = 0;
	else
		options.openState = -1;
}

extern "C" JNIEXPORT void JNICALL
Java_com_onecore_loader_floating_FloatLogo_WideView(JNIEnv *, jobject, jint view) {
	options.wideView = view;
}

extern "C" JNIEXPORT void JNICALL
Java_com_onecore_loader_floating_FloatLogo_Range(JNIEnv *, jobject, jint range) {
	options.aimingRange = 1 + range;
}


extern "C" JNIEXPORT void JNICALL
Java_com_onecore_loader_floating_FloatLogo_distances(JNIEnv *,  jobject ,jint distances) {
    //options.aimingDistances=distances;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_onecore_loader_floating_FloatLogo_recoil(JNIEnv *env, jobject thiz, jint recoil) {
  //  options.RecoilCompt = recoil;
}

extern "C" JNIEXPORT void JNICALL
Java_com_onecore_loader_floating_FloatLogo_Target(JNIEnv *, jobject, jint target) {
	options.aimbotmode = target;
}

extern "C" JNIEXPORT void JNICALL
Java_com_onecore_loader_floating_FloatLogo_AimBy(JNIEnv *, jobject, jint aimby) {
	options.priority = aimby;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_onecore_loader_floating_Overlay_getReady(JNIEnv *, jobject) {
	int sockCheck = 1;
	if (!Create()) {
		perror("Creation failed");
		return false;
	}
	
	setsockopt(sock, SOL_SOCKET, SO_REUSEADDR, &sockCheck, sizeof(int));
	if (!Bind()) {
		perror("Bind failed");
		return false;
	}

	if (!Listen()) {
		perror("Listen failed");
		return false;
	}
	
	if (Accept()) {
		return true;
	}
}



extern "C" JNIEXPORT jstring JNICALL 
Java_com_onecore_loader_BoxApplication_BoxApp(JNIEnv* env, jobject thiz) {
    return env->NewStringUTF(OBFUSCATE("PARALLAX"));
}

