package com.lumina.engine.Game.SceneScript;
import com.lumina.engine.runtime.Utils.SceneScript;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.Gdx;
import com.lumina.engine.runtime.Items.*;
import com.lumina.engine.runtime.Utils.PlayerItem;
import box2dLight.Light;
import com.badlogic.gdx.scenes.scene2d.Actor;

/*
    Hi Coder :)
    & Thanks for using Lumina Engine
    example :
    - Reverse body gravity on create:
        getStage().findItem("body").getBody().setGavityScale(-1);
    - Link to the other script:
        linkTo(OtherScene.class);
        //or
        linkTo(new OtherScene());
*/
public class scene1Script extends SceneScript {
    public void create(){
        //getStage().debugBox2d = true;
        String car = getStage().getValue("car");
        getStage().setCameraX(getStage().findItem(car.equals("")?"c1":car));
        String img = getStage().getValue("wheel");
        if(!img.equals(""))
        for(int x = 0; x < 10; x++){
            PlayerItem wheel = getStage().findItem("w"+(x+1));
            getStage().setImage(wheel,img);
        }

    }
	public void draw(){

	}
	public void pause(){

	}
	public void resume(){

	}
}