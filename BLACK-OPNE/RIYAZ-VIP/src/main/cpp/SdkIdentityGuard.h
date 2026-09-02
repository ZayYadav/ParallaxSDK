#pragma once

#include <jni.h>
#include <string>

jboolean verifyInstalledIdentity(JNIEnv *env, jclass,
                                 jobject context,
                                 jstring expectedPackage,
                                 jstring expectedSigningSha256);

bool verifySignedIdentityBinding(JNIEnv *env,
                                 jclass nativeClass,
                                 jstring canonical,
                                 jstring signatureBase64,
                                 const std::string &currentPackage,
                                 const std::string &currentSigning,
                                 jlong leaseExpiresAt,
                                 jlong serverTime);
