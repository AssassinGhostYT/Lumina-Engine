package com.lumina.engine.editor.ui.sub.inputs;

import com.lumina.engine.runtime.Utils.Utils;

public class IntInput extends FloatInput {
	
	public IntInput(){
		super();
	}
	
	@Override
	public void setValue(String v){
		try {
			int i = Utils.getInt(v);
			value.setText(v);
		} catch(Exception e){}
	}
}