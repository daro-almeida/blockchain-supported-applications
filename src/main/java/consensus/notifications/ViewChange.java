package consensus.notifications;

import java.util.LinkedList;
import java.util.List;

import pt.unl.fct.di.novasys.babel.generic.ProtoNotification;
import pt.unl.fct.di.novasys.network.data.Host;
import utils.View;

public class ViewChange extends ProtoNotification {
	
	public final static short NOTIFICATION_ID = 102;
	
	private final View view;
	
	
	public ViewChange(View view) {
		super(ViewChange.NOTIFICATION_ID);
		this.view = view;
	}

	public View getView() {
		return view;
	}
}
