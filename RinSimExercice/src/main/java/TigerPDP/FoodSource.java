package TigerPDP;

import java.util.ArrayList;

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
	
	FoodSource(ParcelDTO dto) {
		super(dto);
		elements = new ArrayList<FoodElement>();
	}

	@Override
	public void initRoadPDP(RoadModel pRoadModel, PDPModel pPdpModel) {}
	
	public float getFixedCost (){
		return 0;
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
		return elements.remove(0);
	}
	
}
