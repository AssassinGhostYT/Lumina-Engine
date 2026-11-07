package com.lumina.engine.Fragments;
import android.os.Bundle;
import android.view.View;
import androidx.annotation.Nullable;
import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.android.AndroidFragmentApplication;
import com.lumina.engine.Helpers.FileUtil;
import com.lumina.engine.Items.Editor;
import com.lumina.engine.editor.TestApp;
import com.lumina.engine.editor.ui.sub.ConfirmDialog;

public class LibgdxFragment extends AndroidFragmentApplication implements AndroidFragmentApplication.Callbacks {

	private ApplicationListener applicationListener;
	private ConfirmDialog confirmDialog;
	private boolean dialogShown = false;
    
    public LibgdxFragment() {
        this.applicationListener = new com.lumina.engine.editor.TestApp();
    }
    
    public LibgdxFragment(ApplicationListener applicationListener) {
        this.applicationListener = applicationListener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(android.view.LayoutInflater inflater, @Nullable android.view.ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
		// 24-bit depth: the 3D view (editor + play) has no z-fighting between close surfaces
		com.badlogic.gdx.backends.android.AndroidApplicationConfiguration cfg = new com.badlogic.gdx.backends.android.AndroidApplicationConfiguration();
		cfg.depth = 24;
		View view = initializeForView(applicationListener, cfg);
		//FileUtil.writeFile("logs/check.txt","listener : "+(applicationListener!=null)+", view : "+(view!=null));
		return view;
    }

    @Override
    public void exit() {
		if(applicationListener instanceof TestApp){
			TestApp app = ((TestApp)applicationListener);
			if(app.isPlaying())
				app.play(null);
			else {
				Gdx.app.postRunnable(()->{
					if(dialogShown){
						confirmDialog.hide();
						dialogShown = false;
					} else {
						if(confirmDialog == null)
							confirmDialog = new ConfirmDialog("Exit","Do you want to exit ?",(ok)->{
								if(ok){
								    if(app.isProjectOpened()){
									    app.closeProject();
										/*
										if(Editor.getCurrentEditor()!=null&&Editor.getCurrentEditor().getIndexer()!=null){
											try {
										    	Editor.getCurrentEditor().getIndexer().shutdown();
											} catch(Exception ex){}
											
										}
										*/
									} else getActivity().finish();
								}
								dialogShown = false;
							});
						confirmDialog.show(app.getUiStage());
						dialogShown = true;
					}
				});
			}
		}
    }
}