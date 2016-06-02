package TigerPDP;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.experimental.theories.internal.Assignments;

import com.github.rinde.rinsim.core.model.comm.CommDevice;
import com.github.rinde.rinsim.core.model.comm.CommDeviceBuilder;
import com.github.rinde.rinsim.core.model.comm.CommUser;
import com.github.rinde.rinsim.core.model.pdp.Depot;
import com.github.rinde.rinsim.core.model.pdp.PDPModel;
import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.core.model.pdp.Vehicle;
import com.github.rinde.rinsim.core.model.road.RoadModel;
import com.github.rinde.rinsim.core.model.road.RoadUser;
import com.github.rinde.rinsim.core.model.time.TickListener;
import com.github.rinde.rinsim.core.model.time.TimeLapse;
import com.github.rinde.rinsim.geom.Point;
import com.google.common.base.Optional;

public class TaxiBase extends Depot implements CommUser, TickListener {

	private static TaxiBase TAXIBASE;
	Optional<CommDevice> device;
	private ArrayList<GFAnt> taxis = new ArrayList<GFAnt>();
	private ArrayList<Parcel> customers = new ArrayList<Parcel>();
	private HashMap<Vehicle,Parcel> assignments;

	TaxiBase(Point position, double capacity) {
		super(position);
		setCapacity(capacity);
	}

	public static TaxiBase get() {
		return TAXIBASE;
	}

	public static void set(TaxiBase taxibase) {
		if(TAXIBASE == null)
			TAXIBASE = taxibase;
		else throw new IllegalArgumentException();
	}

	public static Parcel getAssignment(Vehicle v) {
		return get().assignments.get(v);
	}

	@Override
	public void initRoadPDP(RoadModel pRoadModel, PDPModel pPdpModel) {}

	@Override
	public void tick(TimeLapse time) {
		//measure time here and in taxi and in simulator somehow to find bottleneck
		final RoadModel rm = getRoadModel();
		final PDPModel pm = getPDPModel();

		assignments = new HashMap<Vehicle,Parcel>();

		//1. get the positions (via comm or references)
		HashMap<Parcel,Point> cPositions = new HashMap<Parcel,Point>();
		for(Parcel c : customers)
			cPositions.put(c,c.getPickupLocation());
		HashMap<Vehicle,Point> vPositions = new HashMap<Vehicle,Point>();
		for(GFAnt v : taxis)
			vPositions.put(v, v.getPosition().get());

		//2. extract closest pair and find next
		ArrayList<GFAnt> remainingTaxis = new ArrayList<GFAnt>(taxis);
		for(GFAnt taxi : taxis){
			if(taxi.isTaken())
				remainingTaxis.remove(taxi);
		}
		ArrayList<Parcel> remainingCustomers = new ArrayList<Parcel>(customers);
		Map<Pair,Double> shortestFound = new HashMap<Pair,Double>();

		while(!remainingCustomers.isEmpty() && !remainingTaxis.isEmpty()) {
			for(Parcel c : remainingCustomers) {
				Point pos = cPositions.get(c);
				Map<Pair,Double> distances = new HashMap<Pair,Double>();
				for(GFAnt v : remainingTaxis) {
					Point taxiPos = vPositions.get(v);
					distances.put(new Pair(v, c), Point.distance(taxiPos, pos));
				}
				distances = MapUtil.sortByValue(distances);
				Pair first = null;
				double firstDist = 10;
				for(Map.Entry<Pair, Double> entry : distances.entrySet()) {		//this for loop is just a complicated way to extract the first element
					if(first == null) {
						first = entry.getKey();
						firstDist = entry.getValue();
					}
				}
				shortestFound.put(first,firstDist);
				//found the shortest for current customer
			}
			// found the shortest for each customer
			//now sort again
			shortestFound = MapUtil.sortByValue(shortestFound);
			Pair absoluteShortest = null;
			//		doub le shortestDist = 10;
			for(Map.Entry<Pair, Double> entry : shortestFound.entrySet()) {
				if(absoluteShortest == null) {
					absoluteShortest = entry.getKey();
					System.out.println("found distance: "+ entry.getValue());
				}
			}
			//we found and now use the absolute minimal distance pair found
			assignments.put(absoluteShortest.v, absoluteShortest.c);
			remainingTaxis.remove(absoluteShortest.v);
			remainingCustomers.remove(absoluteShortest.c);
			shortestFound = new HashMap<Pair,Double>();
		}
		printAssignments(assignments);
	}

	private void printAssignments(HashMap<Vehicle,Parcel> assignments) {
		for(Vehicle v : assignments.keySet()){
			Point taxipos = ((GFAnt) v).getPosition().get();
			Point customerpos = assignments.get(v).getPickupLocation();
			System.out.println("taxi pos: "+taxipos+" customer pos : "+customerpos+" distance :"+Point.distance(taxipos, customerpos));
		}
	}

	@Override
	public Optional<Point> getPosition() {
		return Optional.of(getRoadModel().getPosition(this));
	}

	@Override
	public void setCommDevice(CommDeviceBuilder builder) {
		builder.setMaxRange(15);
		device = Optional.of(builder
				.setReliability(1)
				.build());
	}

	@Override
	public void afterTick(TimeLapse timeLapse) {
	}

	public static void register(GFAnt taxi) {
		get().taxis.add(taxi);
	}

	public static List<GFAnt> getTaxis() {
		return get().taxis;
	}

	public static void register(Parcel customer) {
		get().customers.add(customer);
	}

	public static void remove(Parcel customer) {
		get().customers.remove(customer);
	}

	public static List<Parcel> getCustomer() {
		return get().customers;
	}

	class Pair {
		Vehicle v;
		Parcel c;
		Pair(Vehicle v, Parcel c){
			this.v = v;
			this.c = c;
		}
	}
}