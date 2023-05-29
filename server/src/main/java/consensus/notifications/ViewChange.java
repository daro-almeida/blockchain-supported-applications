package consensus.notifications;

import pt.unl.fct.di.novasys.babel.generic.ProtoNotification;
import utils.View;

public class ViewChange extends ProtoNotification {
	
	public final static short NOTIFICATION_ID = 102;
	
	private final View view;
	
	
	public ViewChange(View view) {
		super(ViewChange.NOTIFICATION_ID);
		this.view = new View(view);
	}

	public View getView() {
		return view;
	}
}
