package TigerPDP;

import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.core.model.pdp.ParcelDTO;

public class FoodElement extends Parcel {
	
	private double cost;
	
	public FoodElement(ParcelDTO parcelDto, double cost) {
		super(parcelDto);
		this.cost = cost;
	}
	
	public double getFixedCost (){
		return cost;
	}
}
