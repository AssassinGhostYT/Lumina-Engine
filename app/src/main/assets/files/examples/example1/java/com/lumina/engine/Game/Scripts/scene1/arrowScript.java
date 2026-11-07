package com.lumina.engine.Game.Scripts.scene1;

import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.lumina.engine.runtime.Utils.PlayerItem;
import com.lumina.engine.runtime.Utils.ItemScript;
import com.lumina.engine.runtime.Items.*;

public class arrowScript extends ItemScript {
/*
    Hi coder... :)
    the engine still in it's beta and you may face so many bugs,
    so please contact us without hesitation...
*/
    float y = 10f, velocity = 0f, increment = 0.1f,rot;
    public void onClick(){
        
    }
    public void onTouchStart(InputEvent event){
        
    }
    public void onTouchEnd(InputEvent event){
        
    }
    public void onBodyCreated(){
        y = body.getPosition().y;
        rot = body.getAngle();
    }
    public void onBodyUpdate(){
        body.setTransform(body.getPosition().x,y  + 20 * velocity,rot);
        velocity+=increment;
        if(velocity >= 1) increment = -0.1f;
        if(velocity <= 0) increment = 0.1f;
    }
    public void onCollisionBegin(PlayerItem other){
        
    }
    public void onCollisionEnd(PlayerItem other){
        
    }
    
}