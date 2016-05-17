package TigerPDP;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.github.rinde.rinsim.core.model.comm.CommDevice;
import com.github.rinde.rinsim.core.model.pdp.Depot;
import com.github.rinde.rinsim.core.model.pdp.PDPModel;
import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.core.model.pdp.Vehicle;
import com.github.rinde.rinsim.core.model.road.RoadModel;
import com.github.rinde.rinsim.core.model.time.TickListener;
import com.github.rinde.rinsim.core.model.time.TimeLapse;
import com.github.rinde.rinsim.geom.Point;
import com.google.common.base.Optional;

public class GradientField extends Depot implements TickListener {

	private static GradientField GRADIENTFIELD;
	private int succesfulDeliveries;
	Optional<CommDevice> device;
	private ArrayList<Ant> ants = new ArrayList<Ant>();
	private ArrayList<FoodSource> sources = new ArrayList<FoodSource>();
	private HashMap<Vehicle,Point> gradientVectors;

	GradientField(Point position, double capacity) {
		super(position);
		setCapacity(capacity);
		gradientVectors = new HashMap<Vehicle,Point>();
		succesfulDeliveries = 0;
	}

	public static GradientField get() {
		return GRADIENTFIELD;
	}

	public static void set(GradientField gradiendField) {
		if(GRADIENTFIELD == null)
			GRADIENTFIELD = gradiendField;
		else throw new IllegalArgumentException();
	}

	@Override
	public void initRoadPDP(RoadModel pRoadModel, PDPModel pPdpModel) {}

	@Override
	public void tick(TimeLapse timeLapse) {
		
		for(Ant ant : ants) {
			Point resultingVector = new Point(0, 0);
			for(Ant other : ants) {
				resultingVector = MapUtil.addPoints(resultingVector, repulsiveField(ant, other));
			}
			for(FoodSource food : sources) {
				resultingVector = MapUtil.addPoints(resultingVector, attractiveField(ant, food));
			}
			resultingVector = MapUtil.normalize(resultingVector);
			gradientVectors.put(ant, resultingVector);
		}
	}

	private Point repulsiveField(Ant ant, Ant other) {
		if(ant.equals(other))
			return new Point(0,0);
		Point p1 = ant.getPosition().get();
		Point p2 = other.getPosition().get();
		double distance = Point.distance(p1, p2);
		if(distance == 0)
			return new Point(0,0);
		//threshold for distance
		Point result = Point.diff(p1, p2);	// debug: order for repulse/attract
		result = MapUtil.normalize(result);
		result = MapUtil.rescale(result, 2/distance/distance);
		return result;
	}
	
	private Point attractiveField(Ant ant, FoodSource food) {
		Point p1 = ant.getPosition().get();
		Point p2 = food.getPickupLocation();
		double distance = Point.distance(p1, p2);
		//TODO: threshold
		Point result = Point.diff(p2, p1);	// debug: order
		result = MapUtil.normalize(result);
		result = MapUtil.rescale(result, 1/distance/distance);
		int remainingFoodModifier = food.getNumberElements(); 	// any other modifiers or does this suffice?
		result = MapUtil.rescale(result, Math.log(remainingFoodModifier+1)+1);
		return result;
	}

	@Override
	public void afterTick(TimeLapse timeLapse) {
		// TODO Auto-generated method stub
	}

	public static void register(Ant ant) {
		get().ants.add(ant);
	}

	public static List<Ant> getAnts() {
		return get().ants;
	}

	public static void register(FoodSource source) {
		get().sources.add(source);
	}

	public static Parcel pickup(FoodSource source) {
		/*get().sources.remove(source);*/
		Parcel food = null;
		if(source.getNumberElements() == 0) {
			get().sources.remove(source);
			return source;
		}
		else food = source.pickup();
		return food;
	}

	public static ArrayList<FoodSource> getFoodSources() {
		return get().sources;
	}

	public static Parcel getFoodFromVisual(Ant ant) {
		return get().nearestVisibleFood(ant);
	}

	public Parcel nearestVisibleFood(Ant ant) {
		// develop method for seeing nearby food
		double shortestDist = 99999999;
		Parcel nearestFood = null;
		for(Parcel parcel : sources) {
			double dist = Point.distance(parcel.getPickupLocation(), ant.getPosition().get());
			if(dist < Ant.VISUAL_RANGE && dist < shortestDist) {
				shortestDist = dist;
				nearestFood = parcel;
			}
		}
		return nearestFood;
	}

	public static Point getGradientField(Ant ant) {
		return get().gradientVectors.get(ant);
	}

	public static void notifyDelivery() {
		get().succesfulDeliveries ++;
	}

	public static int getDeliveryCount() {
		return get().succesfulDeliveries;
	}
}
