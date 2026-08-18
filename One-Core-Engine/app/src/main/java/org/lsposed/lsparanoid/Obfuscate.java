package org.lsposed.lsparanoid;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Compatibility marker retained while loader obfuscation is disabled.
 *
 * Existing source files can keep @Obfuscate without requiring the LSParanoid
 * Gradle transform. This annotation intentionally performs no bytecode work.
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD})
public @interface Obfuscate {
}
