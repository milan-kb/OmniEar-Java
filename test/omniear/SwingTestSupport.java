package omniear;

import java.awt.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import javax.swing.SwingUtilities;

/** Shared test-only Swing operations; component searches are performed on the EDT. */
final class SwingTestSupport {
    private SwingTestSupport() { }

    static <T extends Component> T find(Component root, Class<T> type) {
        return find(root, type, component -> true);
    }

    /** First match in depth-first pre-order, including root and preserving child order. */
    static <T extends Component> T find(Component root, Class<T> type, Predicate<? super T> matches) {
        if (type.isInstance(root) && matches.test(type.cast(root))) return type.cast(root);
        if (root instanceof Container container) for (Component child : container.getComponents()) {
            T found = find(child, type, matches);
            if (found != null) return found;
        }
        return null;
    }

    /** Called off the EDT; only the readiness predicate runs on the EDT. */
    static boolean awaitOnEdt(BooleanSupplier predicate, long timeoutSeconds, long intervalMillis) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        while (System.nanoTime() < deadline) {
            var ready = new AtomicBoolean();
            SwingUtilities.invokeAndWait(() -> ready.set(predicate.getAsBoolean()));
            if (ready.get()) return true;
            Thread.sleep(intervalMillis);
        }
        return false;
    }
}
