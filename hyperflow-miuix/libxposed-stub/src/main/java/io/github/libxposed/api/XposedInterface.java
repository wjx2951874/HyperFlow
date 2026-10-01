package io.github.libxposed.api;

import java.lang.reflect.Member;

/**
 * libxposed API（stub，仅编译期；运行时由 LSPosed 框架提供真实实现，不打包进 APK）
 */
public interface XposedInterface {
    int API_VERSION = 102;

    enum ExceptionMode {
        PROTECTIVE,
        UNPROTECTIVE
    }

    interface Hooker {
        Object intercept(Chain chain) throws Throwable;
    }

    interface Chain {
        Object proceed() throws Throwable;
        java.util.List<Object> getArgs();
        Object getArg(int index);
        Object getResult();
        void setResult(Object result);
        Object getThisObject();
        Member getExecutable();
    }

    interface HookBuilder {
        HookBuilder setExceptionMode(ExceptionMode mode);
        HookHandle intercept(Hooker hooker);
    }

    interface HookHandle {
    }

    interface Invoker {
    }

    interface CtorInvoker {
    }
}
