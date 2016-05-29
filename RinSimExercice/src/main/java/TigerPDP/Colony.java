package TigerPDP;

import com.github.rinde.rinsim.core.model.pdp.Depot;
import com.github.rinde.rinsim.core.model.pdp.PDPModel;
import com.github.rinde.rinsim.core.model.road.RoadModel;
import com.github.rinde.rinsim.geom.Point;


public class Colony extends Depot {
	
	//private int succesfulDeliveries;
	private Point position;

	Colony(Point position, double capacity) {
		super(position);
		setCapacity(capacity);
		this.position = position;
	}

	@Override
	public void initRoadPDP(RoadModel pRoadModel, PDPModel pPdpModel) {
	}

	public Point getPosition() {
		return position;
	}
}
