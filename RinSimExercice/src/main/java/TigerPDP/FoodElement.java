package TigerPDP;

import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.core.model.pdp.ParcelDTO;
import com.github.rinde.rinsim.geom.Point;

public class FoodElement extends FoodSource {
	
	private double cost;
	
	public FoodElement(ParcelDTO parcelDto, double cost) {
		super(parcelDto);
		this.cost = cost;
	}
	
	public double getFixedCost (){
		return cost;
	}
	
//	public boolean isExpired() {
//		
//		return true;
//	}
	
	public FoodElement pickup() {
		System.out.println("picking up a dropped element!");
		return executePickup();
	}

	public void destroy() {
		roadModel.unregister(this);
		pdpModel.unregister(this);
		System.out.println("food element destroyed, with position: "+"("+Math.round(this.getPickupLocation().x)+","+Math.round(this.getPickupLocation().y)+")");
		try {
			this.finalize();
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}

	public void setLocation(Point point) {
		
	}
}
