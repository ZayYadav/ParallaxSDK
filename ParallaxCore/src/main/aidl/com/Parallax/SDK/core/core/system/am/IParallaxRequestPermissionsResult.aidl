// IParallaxRequestPermissionsResult.aidl
package com.Parallax.SDK.core.core.system.am;

interface IParallaxRequestPermissionsResult {
    boolean onResult(int requestCode,in String[] permissions,in int[] grantResults);
}