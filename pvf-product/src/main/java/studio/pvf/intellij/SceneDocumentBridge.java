package studio.pvf.intellij;

import studio.pvf.sceneedit.ScriptEdit;
import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** One source of truth. Refresh/apply/delivery run on the UI thread; parsing runs on the worker. */
final class SceneDocumentBridge<T> implements AutoCloseable {
    interface Port {
        String text(); long version(); boolean writable();
        void write(String label, String value);
        default void writeEdit(ScriptEdit edit,String value){write(edit.label(),value);}
    }
    @FunctionalInterface interface Parser<T> { T parse(String text) throws Exception; }
    record State<T>(long version, String text, T model, boolean writable, String error) {}
    private final Port port;
    private final Parser<T> parser;
    private final Executor worker, ui;
    private final Consumer<State<T>> listener;
    private State<T> state = new State<>(-1, "", null, false, "正在解析…");
    private long generation;
    private boolean closed;

    SceneDocumentBridge(Port port, Parser<T> parser, Executor worker, Executor ui, Consumer<State<T>> listener) {
        this.port = port; this.parser = parser; this.worker = worker; this.ui = ui; this.listener = listener;
    }
    State<T> state() { return state; }
    boolean closed() { return closed; }
    void refresh() {
        if (closed) return;
        long request = ++generation, version = port.version(); String text = port.text(); T last = state.model();
        state = new State<>(version, text, last, false, "正在解析…"); listener.accept(state);
        worker.execute(() -> {
            T parsed = null; String failure = "";
            try { parsed = parser.parse(text); }
            catch (Exception invalid) { failure = invalid.getMessage() == null ? invalid.toString() : invalid.getMessage(); }
            T result = parsed; String error = failure;
            ui.execute(() -> {
                if (closed || generation != request || port.version() != version || !port.text().equals(text)) return;
                state = new State<>(version, text, result == null ? last : result, result != null && error.isEmpty() && port.writable(), error);
                listener.accept(state);
            });
        });
    }
    void apply(long version, ScriptEdit edit) throws IOException {
        if (closed || !state.writable() || state.version() != version || port.version() != version || !port.writable())
            throw new IOException("代码已变化或当前片段无法编辑，请等待解析并重新选择");
        String value = edit.applyTo(port.text());
        if(value.equals(port.text()))return;
        port.writeEdit(edit, value); refresh();
    }
    @Override public void close() { closed = true; generation++; }
}
