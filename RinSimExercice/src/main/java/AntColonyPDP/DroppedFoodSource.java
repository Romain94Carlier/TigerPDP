package AntColonyPDP;

import com.github.rinde.rinsim.core.model.pdp.ParcelDTO;
import com.github.rinde.rinsim.geom.Point;

public class DroppedFoodSource extends FoodSource {

	DroppedFoodSource(ParcelDTO dto, FoodElement fe) {
		super(dto);
		putElement(fe);
	}
	
	public Point getPosition() {
		return roadModel.getPosition(this);
	}

}
