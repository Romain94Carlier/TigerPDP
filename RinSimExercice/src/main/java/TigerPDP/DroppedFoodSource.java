package TigerPDP;

import com.github.rinde.rinsim.core.model.pdp.ParcelDTO;

public class DroppedFoodSource extends FoodSource {

	DroppedFoodSource(ParcelDTO dto, FoodElement fe) {
		super(dto);
		putElement(fe);
	}

}
