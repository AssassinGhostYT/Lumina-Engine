package com.lumina.engine.ElementDefs;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.lumina.engine.runtime.Utils.PropertySet;
import com.lumina.engine.runtime.Utils.PlayerItem;

public interface ElementEvent {
	default public String getName(){
		try {
			return ((Actor)this).getName();
		} catch(Exception ex){
			return "Null";
		}
	}
	public void onClick(PlayerItem current);
	public void onTouchStart(PlayerItem current,InputEvent event);
	public void onTouchEnd(PlayerItem current,InputEvent event);
	public void onBodyCreated(PlayerItem current);
	public void onBodyUpdate(PlayerItem current);
	public void onCollisionBegin(PlayerItem current,PlayerItem body2);
	public void onCollisionEnd(PlayerItem curreny,PlayerItem body2);
}