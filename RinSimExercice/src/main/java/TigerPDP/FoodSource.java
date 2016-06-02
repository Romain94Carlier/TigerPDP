package TigerPDP;

import java.util.ArrayList;
import java.util.Date;

import com.github.rinde.rinsim.core.model.pdp.PDPModel;
import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.core.model.pdp.ParcelDTO;
import com.github.rinde.rinsim.core.model.road.RoadModel;
import com.github.rinde.rinsim.geom.Point;

public class FoodSource extends Parcel {

	/**
	 * A prey with very permissive time windows.
	 */

	private ArrayList<FoodElement> elements;
	protected PDPModel pdpModel;
	protected RoadModel roadModel;
	private int tickCount;

	FoodSource(ParcelDTO dto) {
		super(dto);
		elements = new ArrayList<FoodElement>();
		tickCount = 0;
	}

	@Override
	public void initRoadPDP(RoadModel pRoadModel, PDPModel pPdpModel) {
		pdpModel = pPdpModel;
		roadModel = pRoadModel;
	}

	//I think this should be a small percentage, this is why we return a float
	public float getVariableCost (){
		return 0;
	}

	public Point getDestination (){
		return null;
	}

	public int getLifeSpan(){
		return 0;
	}

	public void putElement(FoodElement food) {
		elements.add(food);
	}

	public int getNumberElements(){
		return elements.size();
	}

	public FoodElement pickup() {
		return executePickup();
	}
	
	protected FoodElement executePickup() {
		FoodElement elem = null;
		if(elements.size() > 0)
			elem = elements.remove(0);
		if(elements.isEmpty()) {
			pdpModel.unregister(this);
			roadModel.unregister(this);
		}
		return elem;
	}

	public boolean isExpired() {
		tickCount ++;
		boolean expired = (tickCount > 6000);
		if(expired) {
			for(FoodElement el : elements) {
				pdpModel.unregister(el);
				roadModel.unregister(el);
			}
			roadModel.unregister(this);
			pdpModel.unregister(this);
			System.out.println("source expired");
			Environment.notifyExpiring(elements.size());
		}
		return expired;
	}

	public Point getPosition() {
		return getPickupLocation();
	}

}
