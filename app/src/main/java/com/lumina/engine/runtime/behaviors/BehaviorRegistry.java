package com.lumina.engine.runtime.behaviors;

import com.lumina.engine.runtime.Items.StageImp;
import com.lumina.engine.runtime.Utils.PlayerItem;

import java.util.ArrayList;

/** Lookups used by the generated scene code and by tools. */
public final class BehaviorRegistry {
	private BehaviorRegistry() {}

	/** Element by name, only if it is of the wanted type (else null): {@code b.hpBar = BehaviorRegistry.ref(this, "hp", ProgressItem.class);} */
	public static <T> T ref(StageImp stage, String name, Class<T> type) {
		if (stage == null || name == null || name.length() == 0) return null;
		int slash = name.indexOf('/');
		if (slash > 0) { // "holder/nodeId": an inner element of a UI holder
			PlayerItem holder = stage.findItem(name.substring(0, slash));
			if (!(holder instanceof com.lumina.engine.runtime.Items.UIHolder)) return null;
			Object node = ((com.lumina.engine.runtime.Items.UIHolder) holder).node(name.substring(slash + 1));
			return type.isInstance(node) ? type.cast(node) : null;
		}
		PlayerItem found = stage.findItem(name);
		return type.isInstance(found) ? type.cast(found) : null;
	}

	/** Elements by name (missing / wrong type ones are skipped), for List&lt;PlayerItem&gt; style parameters. */
	public static <T> ArrayList<T> refs(StageImp stage, Class<T> type, String... names) {
		ArrayList<T> out = new ArrayList<T>();
		if (names != null)
			for (int i = 0; i < names.length; i++) {
				T t = ref(stage, names[i], type);
				if (t != null) out.add(t);
			}
		return out;
	}

	/** Built-in behavior by class simple name (e.g. "Floating"), null if unknown. */
	public static Behavior create(String id) {
		Class<?>[] all = BuiltIns.all();
		for (int i = 0; i < all.length; i++)
			if (all[i].getSimpleName().equals(id)) {
				try {
					return (Behavior) all[i].newInstance();
				} catch (Exception ex) {
					return null;
				}
			}
		// a behavior of the project (java/com/lumina/engine/Game/Behaviors/<id>.java)
		try {
			return (Behavior) Class.forName("com.lumina.engine.Game.Behaviors." + id).newInstance();
		} catch (Throwable ex) {
			return null;
		}
	}
}
