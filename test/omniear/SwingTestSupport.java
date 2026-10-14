package omniear;

import java.awt.*;
import java.util.function.Predicate;

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
}
