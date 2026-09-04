#include <list>
#include <vector>
#include <string>
#include <pthread.h>
#include <thread>
#include <cstring>
#include <jni.h>
#include <fstream>
#include <iostream>
#include <dlfcn.h>
#include <chrono> 
#include <fcntl.h>
#include <sys/stat.h>
#include <cstddef>
#include <cstdint>
#include <semaphore.h>
#include <stdint.h>
#include <sstream>
#include <stdarg.h>
#include <stdio.h>
#include "KittyMemory/MemoryPatch.h"

#define targetLibName oxorany("libUE4.so")
#define targetLibName oxorany("libhdmpve.so")
#define targetLibName oxorany("libAntsVoice.so")
#define targetLibName oxorany("libanogs.so")
#define targetLibName oxorany("libUE4.so")
#define ARM64_SYSREG(reg0, reg1, reg2, reg3, op) (((reg0) & 0x1F) | (((reg1) & 0x1F) << 5) | (((reg2) & 0x7) << 10) | (((reg3) & 0xF) << 16) | (((op) & 0x7) << 20)) 


char *Offset;
#define ret_zero
#define _BYTE  uint8_t
#define _WORD  uint16_t
#define _DWORD uint32_t
#define _QWORD __int64
#define _OWORD uint64_t
#define _QWORD uint64_t
#define _BOOL8 uint64_t

typedef long long int64; 
typedef short int16;     

uintptr_t UE4;
uintptr_t ANOGS;


DWORD libanogsBase = 0;
DWORD libUE4Base = 0;
DWORD libanortBase = 0;
DWORD libEGLBase = 0;
DWORD libanogsAlloc = 0;
DWORD libUE4Alloc = 0;
DWORD libEGLAlloc = 0;
DWORD libanogsSize = 0;// 0x3856E5  3.6.0
DWORD libUE4Size = 0;// 0x7CF8F10  3.6.0
unsigned int AnogsSize = 0;
unsigned int UE4Size = 0;
uintptr_t UE4Alloc = 0;
uintptr_t AnogsAlloc = 0;

DWORD NewBase = 0;

DWORD libanogsEnd = 0;//

__int64 ParallaxCoreReturnZero()
{
  return 0LL;
}


void *anogs_thread(void *){
    ANOGS = Tools::GetBaseAddress(oxorany("libanogs.so"));
    while (!ANOGS) {
        ANOGS = Tools::GetBaseAddress(oxorany("libanogs.so"));
        sleep(1);
    }
    while (!isLibraryLoaded(oxorany("libanogs.so"))){
        sleep(1);
    }
    LOGI("ParallaxCore runtime initialized");
    
HOOK_LIB_NO_ORIG("libanogs.so", "0x51FA80", ParallaxCoreReturnZero);

return NULL;
}

void *ue4_thread(void *) {
    do {
    sleep(1);
    } while (!isLibraryLoaded("libUE4.so"));

   return NULL;
}


__attribute__((constructor)) void mainload() {
    pthread_t ptid;
	pthread_create(&ptid, NULL, ue4_thread, NULL);
    pthread_create(&ptid, NULL, anogs_thread, NULL);
}
