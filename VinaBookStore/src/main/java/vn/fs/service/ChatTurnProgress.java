package vn.fs.service;

import java.util.function.Consumer;

/** Progress carries stages only; unverified generated text is never streamed. */
public final class ChatTurnProgress {
    private static final ThreadLocal<Consumer<String>> listener=new ThreadLocal<>();
    public static void listen(Consumer<String> value){listener.set(value);}
    public static void clear(){listener.remove();}
    public static void report(String stage){if(Thread.currentThread().isInterrupted())throw new IllegalStateException("Chat interrupted");var callback=listener.get();if(callback!=null)callback.accept(stage);}
}
