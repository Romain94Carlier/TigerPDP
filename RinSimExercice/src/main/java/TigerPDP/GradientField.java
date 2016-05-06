package TigerPDP;

import java.awt.image.RescaleOp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.sql.PooledConnection;

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
	Optional<CommDevice> device;
	private ArrayList<Ant> ants = new ArrayList<Ant>();
	private ArrayList<FoodSource> sources = new ArrayList<FoodSource>();
	private HashMap<Vehicle,Point> gradientVectors;

	GradientField(Point position, double capacity) {
		super(position);
		setCapacity(capacity);
	}

	public static GradientField get() {
		return GRADIENTFIELD;
	}

	public static void set(GradientField taxibase) {
		if(GRADIENTFIELD == null)
			GRADIENTFIELD = taxibase;
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
		}
	}

	private Point repulsiveField(Ant ant, Ant other) {
		if(ant.equals(other))
			return new Point(0,0);
		Point p1 = ant.getPosition().get();
		Point p2 = other.getPosition().get();
		double distance = Point.distance(p1, p2);
		//threshold for distance
		Point result = Point.diff(p1, p2);	// debug: order for repulse/attract
		result = MapUtil.normalize(result);
		result = MapUtil.rescale(result, 1/distance);
		return result;
	}
	
	private Point attractiveField(Ant ant, FoodSource food) {
		Point p1 = ant.getPosition().get();
		Point p2 = food.getPickupLocation();
		double distance = Point.distance(p1, p2);
		//TODO: threshold
		Point result = Point.diff(p2, p1);	// debug: order
		result = MapUtil.normalize(result);
		result = MapUtil.rescale(result, 1/distance);
		int remainingFoodModifier = food.getNumberElements(); 	// any other modifiers or does this suffice?
		result = MapUtil.rescale(result, remainingFoodModifier);
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

	public static void remove(Parcel source) {
		get().sources.remove(source);
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
}
