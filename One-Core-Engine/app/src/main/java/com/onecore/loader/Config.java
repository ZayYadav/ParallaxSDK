package com.onecore.loader;

import com.onecore.loader.security.NativeStringVault;
import org.lsposed.lsparanoid.Obfuscate;

@Obfuscate
public class Config {

    public static final String[] GAME_LIST_PKG = NativeStringVault.gamePackages();
    public static final int USER_ID = 0;
}
