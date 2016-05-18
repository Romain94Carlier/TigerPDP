package TigerPDP;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.github.rinde.rinsim.core.model.comm.CommDevice;
import com.github.rinde.rinsim.core.model.pdp.Depot;
import com.github.rinde.rinsim.core.model.pdp.PDPModel;
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
	private Point position;

	GradientField(Point position, double capacity) {
		super(position);
		setCapacity(capacity);
		gradientVectors = new HashMap<Vehicle,Point>();
		succesfulDeliveries = 0;
		this.position = position;
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
	public void initRoadPDP(RoadModel pRoadModel, PDPModel pPdpModel) {
	}

	@Override
	public void tick(TimeLapse timeLapse) {
		// 1: check starving and empty food sources
		for(FoodSource source : new ArrayList<FoodSource>(sources)) {
			if(source.isExpired()) {
				sources.remove(source);
			}
		}
		// 2: calculate gradient fields
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
		
		System.out.println("ant 1 energy: "+ants.get(0).getEnergy());
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

	public static FoodElement pickup(FoodSource source) {
		/*get().sources.remove(source);*/
		
		FoodElement food = source.pickup();
		if(source.getNumberElements() == 0) {
			get().sources.remove(source);
		}
		return food;
	}

	public static ArrayList<FoodSource> getFoodSources() {
		return get().sources;
	}

	public static FoodSource getFoodFromVisual(Ant ant) {
		return get().nearestVisibleFood(ant);
	}

	public FoodSource nearestVisibleFood(Ant ant) {
		// develop method for seeing nearby food
		double shortestDist = 99999999;
		FoodSource nearestFood = null;
		for(FoodSource foodSource : sources) {
			double dist = Point.distance(foodSource.getPickupLocation(), ant.getPosition().get());
			if(dist < Ant.VISUAL_RANGE && dist < shortestDist) {
				shortestDist = dist;
				nearestFood = foodSource;
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
	
	public static Point getPosition() {
		return get().position;
	}

	public static void dropFood(FoodElement el) {
	}
}
