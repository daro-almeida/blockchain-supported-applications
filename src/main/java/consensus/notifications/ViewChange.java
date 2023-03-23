package consensus.notifications;

import java.util.LinkedList;
import java.util.List;

import pt.unl.fct.di.novasys.babel.generic.ProtoNotification;
import pt.unl.fct.di.novasys.network.data.Host;

public class ViewChange extends ProtoNotification {
	
	public final static short NOTIFICATION_ID = 102;
	
	private final List<Host> view;
	private final int viewNumber;
	
	
	public ViewChange(List<Host> view, int viewNumber) {
		super(ViewChange.NOTIFICATION_ID);
		this.view = new LinkedList<Host>();
		for(int i = 0; i < view.size(); i++) {
			this.view.add(view.get(i));
		}
		
		this.viewNumber = viewNumber;
	}


	public List<Host> getView() {
		return view;
	}


	public int getViewNumber() {
		return viewNumber;
	}

	

}
