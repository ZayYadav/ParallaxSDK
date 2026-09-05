#pragma once

#include <jni.h>
#include <string>

namespace ClipboardKeyAuth {

// Reads the current primary clipboard item through the supplied Android Context.
// Returns only a value that matches the Parallax key shape; all other clipboard
// contents are ignored.
std::string ReadCandidate(JNIEnv *env, jobject context);

// Standalone validator so this file can be reused from another native library.
bool LooksLikeKey(const std::string &value);

}  // namespace ClipboardKeyAuth
