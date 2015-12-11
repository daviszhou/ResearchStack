package co.touchlab.researchstack.glue.ui.scene;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import co.touchlab.researchstack.core.ui.scene.SceneImpl;
import co.touchlab.researchstack.glue.R;

public class SignUpIneligibleScene extends SceneImpl
{

    public SignUpIneligibleScene(Context context)
    {
        super(context);
    }

    public SignUpIneligibleScene(Context context, AttributeSet attrs)
    {
        super(context, attrs);
    }

    public SignUpIneligibleScene(Context context, AttributeSet attrs, int defStyleAttr)
    {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public View onCreateBody(LayoutInflater inflater, ViewGroup parent)
    {
        return inflater.inflate(R.layout.item_ineligible, parent, false);
    }

    @Override
    public void onBodyCreated(View body)
    {
        hideNextButtons();
    }

}
