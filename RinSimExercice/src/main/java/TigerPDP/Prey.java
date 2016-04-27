package TigerPDP;

import com.github.rinde.rinsim.core.model.pdp.PDPModel;
import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.core.model.pdp.ParcelDTO;
import com.github.rinde.rinsim.core.model.road.RoadModel;
import com.github.rinde.rinsim.geom.Point;

public abstract class Prey extends Parcel {

	/**
	 * A prey with very permissive time windows.
	 */
	Prey(ParcelDTO dto) {
		super(dto);
	}

	@Override
	public void initRoadPDP(RoadModel pRoadModel, PDPModel pPdpModel) {}
	
	public abstract int getFixedCost ();
	
	public abstract Point getDestination ();
}
