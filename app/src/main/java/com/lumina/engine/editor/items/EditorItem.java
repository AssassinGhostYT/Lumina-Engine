package com.lumina.engine.editor.items;

import com.badlogic.gdx.physics.box2d.Body;
import com.lumina.engine.Helpers.PropertySet;

public interface EditorItem {
	public PropertySet<String,Object> getPropertySet();
	public void update();
	public void setProperties(PropertySet<String,Object> propertySet);
	public String getTypeName();
	public default Body getBody(){
		return null;
	}
}