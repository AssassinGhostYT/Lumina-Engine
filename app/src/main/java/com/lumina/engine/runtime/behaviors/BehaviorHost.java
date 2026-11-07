package com.lumina.engine.runtime.behaviors;

import com.lumina.engine.runtime.Utils.ItemScript;
import com.lumina.engine.runtime.Utils.PlayerItem;

/** Script installed on an element that has behaviors but no script of its own. Does nothing by itself. */
public class BehaviorHost extends ItemScript {
	public BehaviorHost(PlayerItem item) {
		super(item);
	}
}
