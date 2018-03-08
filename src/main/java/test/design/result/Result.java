package test.design.result;


import javafx.util.Pair;

import java.util.function.Consumer;
import java.util.function.Function;

@SuppressWarnings("unchecked")
public final class Result<ERR, RES> {
    private final boolean isError;

    public static <RES, Ex extends java.lang.Throwable> Result<Ex, RES> fromThrowable(ThrowingFunction<RES, Ex> c) {
        try {
            RES result = c.call();
            return ok(result);
        } catch (java.lang.Throwable e) {
            return error((Ex) e);
        }
    }

    public static <ERR, RES> Result<ERR, RES> error(ERR err) {
        return new Result<>(err, true);
    }

    public static <ERR, RES> Result<ERR, RES> ok(RES res) {
        return new Result<>(res, false);
    }

    private final Object result;
    private Consumer<ERR> errHandler;

    private Result(Object res, boolean isError) {
        this.result = res;
        this.isError = isError;
    }

    public Safe<ERR, RES> onError(Consumer<ERR> c) {
        this.errHandler = c;
        if (isError) {
            this.errHandler.accept((ERR) result);
        }
        return new SafeResult();
    }


    public Get<ERR, RES> onErrorThrow() throws Exception {
        if (isError)
            throw new Exception(String.valueOf(result));
        else
            return new SafeResult();
    }

    public <Ex extends java.lang.Throwable> Get<ERR, RES> onErrorThrow(ThrowFromErr<ERR, Ex> t) throws Ex {
        this.errHandler = (ex) -> {
            try {
                t.doThrow(ex);
            } catch (Throwable err) {
                sneakyThrow(err);
        }
    };
        if (isError) {
            errHandler.accept((ERR)result);
            throw new IllegalStateException("ERROR should be thrown");
        } else
            return new SafeResult();
    }

    public <Ex extends java.lang.Throwable> Get<ERR, RES> onErrorThrow(Throw<Ex> t) throws Ex {
        this.errHandler = $ -> {
            try {
                t.doThrow();
            } catch (Throwable err) {
                sneakyThrow(err);
        }
    };
        if (isError) {
            errHandler.accept((ERR)result);
            throw new IllegalStateException("ERROR should be thrown");
        } else
            return new SafeResult();
    }

    private static <EX extends Throwable> void sneakyThrow(Throwable ex) throws EX {
        throw (EX) ex;
    }

    public interface Safe<ERR, RES> {
        void onSuccess(Consumer<RES> c);

        <NEXT> Safe<ERR, NEXT> then(Function<RES, Result<ERR, NEXT>> next);

        <ERR2, NEXT> Safe<ERR, NEXT> then(Function<RES, Result<ERR2, NEXT>> next, Function<ERR2, ERR> errMapping);
    }

    public interface Get<ERR, RES> extends Safe<ERR, RES> {
        RES get();
    }

    @FunctionalInterface
    public interface ThrowFromErr<ERR, Ex extends java.lang.Throwable> {
        void doThrow(ERR err) throws Ex;
    }

    @FunctionalInterface
    public interface Throw<Ex extends java.lang.Throwable> {
        void doThrow() throws Ex;
    }

    @FunctionalInterface
    public interface ThrowingFunction<R, Ex extends java.lang.Throwable> {
        R call() throws Ex;
    }

    private class SafeResult implements Safe<ERR, RES>, Get<ERR, RES> {
        @Override
        public RES get() {
            return (RES) result;
        }

        @Override
        public void onSuccess(Consumer<RES> c) {
            if (!isError)
                c.accept((RES) result);
        }

        @Override
        public <NEXT> Safe<ERR, NEXT> then(Function<RES, Result<ERR, NEXT>> next) {
            if (isError) {
                return Result.<ERR, NEXT>error((ERR) result)
                        .onError(errHandler);
            } else {
                return next.apply((RES) result)
                        .onError(errHandler);
            }
        }

        @Override
        public <ERR2, NEXT> Safe<ERR, NEXT> then(Function<RES, Result<ERR2, NEXT>> next, Function<ERR2, ERR> errMapping) {
            if (isError) {
                return Result.<ERR, NEXT>error((ERR) result)
                        .onError(errHandler);
            } else {
                MutableReference<Pair<Boolean, Object>> res = new MutableReference<>();
                next.apply((RES) result)
                        .onError(e -> res.set(new Pair<>(true, e)))
                        .onSuccess(r -> res.set(new Pair<>(false, r)));

                boolean isError = res.get().getKey();
                if (isError) {
                    ERR2 err = (ERR2) res.get().getValue();
                    return Result.<ERR, NEXT>error(errMapping.apply(err))
                            .onError(errHandler);
                } else {
                    return Result.<ERR, NEXT>ok((NEXT) res.get().getValue())
                            .onError(errHandler);
                }
            }
        }
    }

    static class MutableReference<T> {
        private T value;

        void set(T value) {
            this.value = value;
        }

        T get() {
            return this.value;
        }
    }
}
