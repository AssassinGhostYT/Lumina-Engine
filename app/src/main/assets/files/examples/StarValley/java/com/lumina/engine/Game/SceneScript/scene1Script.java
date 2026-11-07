package com.lumina.engine.Game.SceneScript;
import com.lumina.engine.runtime.Utils.SceneScript;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.Gdx;
import com.lumina.engine.runtime.Items.*;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.graphics.Texture;
import com.lumina.engine.runtime.Utils.PlayerItem;
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
    PlayerItem joystick,player;
    public void create(){
        joystick = getStage().findItem("Joystick1");
        player = getStage().findItem("player");
    }
    public void debug(String str){
        getStage().debug(str);
    }
	public void draw(){
        if(joystick.getPower()!=0)
            player.getBody().setTransform(player.getBody().getPosition(),(float)Math.toRadians(joystick.getAngleDegrees() - 180));
	    player.getBody().setLinearVelocity(new Vector2(joystick.getJoyStickX()*250,joystick.getJoyStickY()*250));

	}
	public void pause(){

	}
	public void resume(){

	}
}