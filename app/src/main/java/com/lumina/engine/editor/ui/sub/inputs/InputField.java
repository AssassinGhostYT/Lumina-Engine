package com.lumina.engine.editor.ui.sub.inputs;

import com.lumina.engine.editor.TestApp;
import com.lumina.engine.editor.utils.Lang;

public interface InputField {
	public void setNameText(String name);
	public void setValue(String value);
	public String getValue();
	public String getFieldName();
	public void setOnChange(Runnable runnable);
	default public void showDetails(){
		TestApp app = TestApp.getCurrentApp();
		String details = Lang.getTrans(getFieldName()+"Details");
		if(!details.equals(getFieldName()+"Details")){
			app.getSimpleNote().setMessage(details)
					.show(app.getUiStage());
		}
	}
}