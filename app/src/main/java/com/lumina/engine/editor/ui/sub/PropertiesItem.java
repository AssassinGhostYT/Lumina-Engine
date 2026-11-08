package com.lumina.engine.editor.ui.sub;

import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.utils.Array;
import com.google.gson.reflect.TypeToken;
import com.google.gson.Gson;
import com.kotcrab.vis.ui.widget.VisScrollPane;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.lumina.engine.Helpers.PropertySet;
import com.lumina.engine.editor.LibgdxEditor;
import com.lumina.engine.editor.TestApp;
import com.lumina.engine.runtime.Utils.Utils;
import java.util.HashMap;

public class PropertiesItem extends Group {
	TestApp app;
	VisScrollPane scrollPane;
	VisTable table;
	Array<SectionItem> sectionItems = new Array<>();
	VisTable inspectorHeader;
	VisLabel selectionLabel;
	VisTextField searchField;
	public PropertiesItem(TestApp app){
		super();
		this.app = app;
		init();
	}
	
	private void init(){
		inspectorHeader = new VisTable();
		selectionLabel = new VisLabel("Inspector");
		searchField = new VisTextField("");
		searchField.setMessageText("Buscar propiedad...");
		searchField.addListener(new ChangeListener(){ @Override public void changed(ChangeListener.ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor){ refresh(); }});
		inspectorHeader.add(selectionLabel).left().growX().padLeft(8);
		inspectorHeader.add(searchField).width(190).pad(6);
		addActor(inspectorHeader);
		table = new VisTable();
		scrollPane = new VisScrollPane(table);
		scrollPane.setFlickScroll(true);
		scrollPane.setBounds(0, 0, getWidth(), Math.max(0, getHeight() - 58));
		addActor(scrollPane);
		//load properties
		String keys=",";
		HashMap<String,Object> map=null;
		//editor.setProperties(this);
		try {
			map = new Gson().fromJson(Utils.internal("types.json").readString(), new TypeToken<HashMap<String, Object>>(){}.getType());
		} catch(Exception ex){
			app.toast("Map init error : "+ex.toString());
			return;
		}
		
		HashMap<String,Object> propertiesMap = new HashMap<>();
		for(String s:map.keySet()){
			for(String str:map.get(s).toString().split(",")){
				if(str.equals("")) continue;
				propertiesMap.put(str,s);
			}
		}
		
		try {
			map = new Gson().fromJson(Utils.internal("map.json").readString(), new TypeToken<HashMap<String, Object>>(){}.getType());
		} catch(Exception ex){
			app.toast("editor map.json error : "+ex.toString());
			com.badlogic.gdx.Gdx.files.external("logs/app_logs.txt").writeString("editor map.json error : "+ex.toString()+"\n",true);
		}
		
		for(String s:map.keySet()){
			if(s.equals("static")) continue;
			keys+=s+":\n";
			SectionItem section = new SectionItem(s);
			for(String str:map.get(s).toString().split(",")){
				keys+=str+",";
				if(str.equals("")) continue;
				try {
					if(propertiesMap.get(str).equals("static")) continue;
					EditorField editorField = new EditorField(app,str,propertiesMap.get(str).toString());
					section.addField(editorField);
					//Log.e(Utils.error_tag,"√ key : "+str);
				} catch(Exception ex){
					String msg = "field error : "+ex.toString()+",name : "+str+",type : "+propertiesMap.get(str);
					app.toast(msg);
					com.badlogic.gdx.Gdx.files.external("logs/app_logs.txt").writeString(msg+"\n",true);
					//Log.e(Utils.error_tag,"key : "+str+"\n"+ex.toString());
				}
			}
			PropertySet<String,Object> propertySet = app.getEditor().getSelectedActor() != null ? PropertySet.getPropertySet(app.getEditor().getSelectedActor()) : null;
			if(propertySet!=null)
				section.update(propertySet);
			table.add(section).growX().row();
			sectionItems.add(section);
		}
		//com.badlogic.gdx.Gdx.files.external("logs/app_logs.txt").writeString(i+"",false);
	}
	
	@Override protected void sizeChanged(){ super.sizeChanged(); if(inspectorHeader != null) inspectorHeader.setBounds(0, getHeight()-58, getWidth(), 58); if(scrollPane != null) scrollPane.setBounds(0, 0, getWidth(), Math.max(0, getHeight()-58)); }

	public void refresh(){
		String selectedName = app.getEditor().getSelectedActor() == null ? "Inspector" : "Inspector · " + app.getEditor().getSelectedActor().getName();
		selectionLabel.setText(selectedName);
		String query = searchField == null ? "" : searchField.getText().trim().toLowerCase();
		PropertySet<String,Object> propertySet = (app.getEditor().getSelectedActor() == null ? null : PropertySet.getPropertySet(app.getEditor().getSelectedActor()));
		table.clear();
		for(SectionItem sectionItem:sectionItems){
			try {
				if(sectionItem.isVisible(propertySet) && sectionItem.matches(query)){
					table.add(sectionItem).growX().row();
					sectionItem.update(propertySet);
				}
			} catch(Exception exception){
				com.badlogic.gdx.Gdx.files.external("logs/app_logs.txt").writeString("properties item error : "+exception.toString()+"\n",true);
			}
		}
	}
	
}