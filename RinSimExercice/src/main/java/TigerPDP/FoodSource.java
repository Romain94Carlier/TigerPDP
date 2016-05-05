package TigerPDP;

import com.github.rinde.rinsim.core.model.pdp.PDPModel;
import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.core.model.pdp.ParcelDTO;
import com.github.rinde.rinsim.core.model.road.RoadModel;
import com.github.rinde.rinsim.geom.Point;

public abstract class FoodSource extends Parcel {

	/**
	 * A prey with very permissive time windows.
	 */
	
	FoodSource(ParcelDTO dto) {
		super(dto);
	}

	@Override
	public void initRoadPDP(RoadModel pRoadModel, PDPModel pPdpModel) {}
	
	public abstract int getFixedCost ();
	
	//I think this should be a small percentage, this is why we return a float
	public abstract float getVariableCost ();
	
	public abstract Point getDestination ();
	
	public abstract int getLifeSpan();
	
	public abstract int getLocation();
	
}
