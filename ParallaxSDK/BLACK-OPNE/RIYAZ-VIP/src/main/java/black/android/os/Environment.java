package black.android.os;

import top.niunaijun.blackreflection.annotation.BClassName;
import top.niunaijun.blackreflection.annotation.BStaticField;

@BClassName("android.os.Environment")
public interface Environment {

  @BStaticField
  Object sCurrentUser();
}