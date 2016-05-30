package TigerPDP;
/*
 * Copyright (C) 2011-2015 Rinde van Lon, iMinds-DistriNet, KU Leuven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import static com.google.common.collect.Maps.newHashMap;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.apache.commons.math3.random.RandomGenerator;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Monitor;

import com.github.rinde.rinsim.core.Simulator;
import com.github.rinde.rinsim.core.model.pdp.DefaultPDPModel;
import com.github.rinde.rinsim.core.model.pdp.Depot;
import com.github.rinde.rinsim.core.model.pdp.PDPModel;
import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.core.model.pdp.ParcelDTO;
import com.github.rinde.rinsim.core.model.pdp.Vehicle;
import com.github.rinde.rinsim.core.model.road.RoadModel;
import com.github.rinde.rinsim.core.model.road.RoadModelBuilders;
import com.github.rinde.rinsim.core.model.road.RoadUser;
import com.github.rinde.rinsim.core.model.time.TickListener;
import com.github.rinde.rinsim.core.model.time.TimeLapse;
import com.github.rinde.rinsim.event.Listener;
//import com.github.rinde.rinsim.examples.core.taxi.TaxiRenderer;//.TaxiRenderer.Language;
import com.github.rinde.rinsim.geom.Graph;
import com.github.rinde.rinsim.geom.MultiAttributeData;
import com.github.rinde.rinsim.geom.Point;
import com.github.rinde.rinsim.geom.io.DotGraphIO;
import com.github.rinde.rinsim.geom.io.Filters;
import com.github.rinde.rinsim.ui.View;
import com.github.rinde.rinsim.ui.renderers.GraphRoadModelRenderer;
import com.github.rinde.rinsim.ui.renderers.PDPModelRenderer;
import com.github.rinde.rinsim.ui.renderers.PlaneRoadModelRenderer;
import com.github.rinde.rinsim.ui.renderers.RoadUserRenderer;

/**
 * Example showing a fleet of taxis that have to pickup and transport customers
 * around the city of Leuven.
 * <p>
 * If this class is run on MacOS it might be necessary to use
 * -XstartOnFirstThread as a VM argument.
 * @author Rinde van Lon
 */
public final class Environment {

	private static final int NUM_DEPOTS = 1;
	private static final int NUM_TAXIS = 1;
	private static final int NUM_CUSTOMERS = 20;

	// time in ms
	private static final long SERVICE_DURATION = 60000;
	private static final int TAXI_CAPACITY = 10;
	private static final int DEPOT_CAPACITY = 100;
	private static final int MAX_SOURCES = 1; //Maximum amount of food sources at the same time

	private static final int SPEED_UP = 4;
	private static final int MAX_CAPACITY = 3;
	private static final double NEW_CUSTOMER_PROB = .007*1/NUM_CUSTOMERS;

	private static final String MAP_FILE = "/data/maps/leuven-simple.dot";
	private static final Map<String, Graph<MultiAttributeData>> GRAPH_CACHE =
			newHashMap();

	private static final long TEST_STOP_TIME = 20 * 60 * 1000;
	private static final int TEST_SPEED_UP = 64;

	//plane params
	static final double VEHICLE_SPEED_KMH = 50d;
	static final boolean BOLD_AGENTS = true;		//try out different strategies
	static final boolean DYNAMIC_AGENTS = true;
	static final Point MIN_POINT = new Point(0, 0);
	static final Point MAX_POINT = new Point(10, 10);

	//Gradient field parameters
	private static Colony COLONY; // improve to ArrayList in case we want more than one colony
	private static ArrayList<Ant> ANTS = new ArrayList<Ant>();
	private static ArrayList<FoodSource> SOURCES = new ArrayList<FoodSource>();
	private static HashMap<FoodElement,Point> DROPPED_FOOD_ELEMENTS = new HashMap<FoodElement,Point>();
	private static HashMap<Vehicle,Point> GRADIENT_VECTORS = new HashMap<Vehicle,Point>();
	private static int SUCCESSFUL_DELIVERIES = 0;

	private Environment() {}

	/**
	 * Starts the {@link TaxiExample}.
	 * @param args The first option may optionally indicate the end time of the
	 *          simulation.
	 */
	public static void main(@Nullable String[] args) {
		final long endTime = args != null && args.length >= 1 ? Long
				.parseLong(args[0]) : Long.MAX_VALUE;

				final String graphFile = args != null && args.length >= 2 ? args[1]
						: MAP_FILE;
				run(false, endTime, graphFile, "gradient field", null /* new Display() */, null, null);
	}

	/**
	 * Run the example.
	 * @param testing If <code>true</code> enables the test mode.
	 */
	public static void run(boolean testing) {
		run(testing, Long.MAX_VALUE, MAP_FILE, "gradient field", null, null, null);
	}

	/**
	 * Starts the example.
	 * @param testing Indicates whether the method should run in testing mode.
	 * @param endTime The time at which simulation should stop.
	 * @param graphFile The graph that should be loaded.
	 * @param display The display that should be used to show the ui on.
	 * @param m The monitor that should be used to show the ui on.
	 * @param list A listener that will receive callbacks from the ui.
	 * @return The simulator instance.
	 */
	public static Simulator run(boolean testing, final long endTime,
			String graphFile, String setup,
			@Nullable Display display, @Nullable Monitor m, @Nullable Listener list) {

		final View.Builder view = createGui(testing, display, m, list);

		// use map of leuven

		final Simulator simulator = Simulator.builder()
				//.addModel(RoadModelBuilders.staticGraph(loadGraph(graphFile)))
				.addModel(
						RoadModelBuilders.plane()
						.withMinPoint(MIN_POINT)
						.withMaxPoint(MAX_POINT)
						.withMaxSpeed(VEHICLE_SPEED_KMH))
				.addModel(DefaultPDPModel.builder())
				.addModel(view)
				.build();
		final RandomGenerator rng = simulator.getRandomGenerator();

		final RoadModel roadModel = simulator.getModelProvider().getModel(
				RoadModel.class);
		// add depots, taxis and parcels to simulator
		//registerCentralizedSimulator(endTime, simulator, rng, roadModel);
		registerGradientFieldSimulator(endTime, simulator, rng, roadModel);
		simulator.start();

		return simulator;
	}

	private static void registerCentralizedSimulator(final long endTime,
			final Simulator simulator, final RandomGenerator rng,
			final RoadModel roadModel) {
		TaxiBase tb = new TaxiBase(roadModel.getRandomPosition(rng), DEPOT_CAPACITY);
		TaxiBase.set(tb);
		for (int i = 0; i < NUM_DEPOTS; i++) {
			simulator.register(TaxiBase.get());
		}
		for (int i = 0; i < NUM_TAXIS; i++) {
			Ant nt = new Ant(roadModel.getRandomPosition(rng), TAXI_CAPACITY, BOLD_AGENTS, DYNAMIC_AGENTS);
			simulator.register(nt);
			TaxiBase.register(nt);
		}
		for (int i = 0; i < NUM_CUSTOMERS; i++) {
			FoodSource nc = new FoodSource(
					Parcel.builder(roadModel.getRandomPosition(rng),
							roadModel.getRandomPosition(rng))
					.serviceDuration(SERVICE_DURATION)
					.neededCapacity(1 + rng.nextInt(MAX_CAPACITY))
					.buildDTO());
			simulator.register(nc);
			TaxiBase.register(nc);
		}

		simulator.addTickListener(new TickListener() {
			@Override
			public void tick(TimeLapse time) {
				if (time.getStartTime() > endTime) {
					simulator.stop();
				} else if (rng.nextDouble() < NEW_CUSTOMER_PROB) {
					FoodSource nc = new FoodSource(
							Parcel.builder(roadModel.getRandomPosition(rng),
									roadModel.getRandomPosition(rng))
							.serviceDuration(SERVICE_DURATION)
							.neededCapacity(1 + rng.nextInt(MAX_CAPACITY))
							.buildDTO());
					simulator.register(nc);
					TaxiBase.register(nc);
				}
			}

			@Override
			public void afterTick(TimeLapse timeLapse) {}
		});
	}

	private static void registerGradientFieldSimulator(final long endTime,
			final Simulator simulator, final RandomGenerator rng,
			final RoadModel roadModel) {

		//Environment env = new Environment();
		//Environment.set(env);
		//simulator.register(env);

		for (int i = 0; i < NUM_DEPOTS; i++) {
			Colony colony = new Colony (MapUtil.rescale(Point.diff(MAX_POINT, MIN_POINT),0.5), DEPOT_CAPACITY);
			simulator.register(colony); 
			//Environment.register(colony);
			register(colony);
		}

		for (int i = 0; i < NUM_TAXIS; i++) {
			Ant newAnt = new Ant(roadModel.getRandomPosition(rng), TAXI_CAPACITY, BOLD_AGENTS, DYNAMIC_AGENTS);
			simulator.register(newAnt);
			//Environment.register(newAnt);
			register(newAnt);
		}

		Point foodPosition = roadModel.getRandomPosition(rng);
		FoodSource nfs = new FoodSource(
				Parcel.builder(foodPosition,
						MapUtil.rescale(Point.diff(MAX_POINT, MIN_POINT),0.5))
				.serviceDuration(SERVICE_DURATION)
				.neededCapacity(1)
				.buildDTO());
		simulator.register(nfs);

		for (int i = 0; i < NUM_CUSTOMERS; i++) {
			FoodElement nfe = new FoodElement(
					Parcel.builder(foodPosition,
							MapUtil.rescale(Point.diff(MAX_POINT, MIN_POINT),0.5))
					.serviceDuration(SERVICE_DURATION)
					.neededCapacity(1)
					.buildDTO(), Math.random() * 2 + 1);
			simulator.register(nfe);
			nfs.putElement(nfe);
		}

		//Environment.register(nfs);
		register(nfs);

		simulator.addTickListener(new TickListener() {
			@Override
			public void tick(TimeLapse time) {
				if((simulator.getCurrentTime() % 3600000) == 0)
					System.out.println("succesfulDeliveries: "+ getDeliveryCount());
				if (time.getStartTime() > endTime) {
					simulator.stop();
				} else 
				{

					if (rng.nextDouble() < NEW_CUSTOMER_PROB && SOURCES.size() < MAX_SOURCES) {
						Point foodPosition = roadModel.getRandomPosition(rng);
						FoodSource nfs = new FoodSource(
								Parcel.builder(foodPosition,
										MapUtil.rescale(Point.diff(MAX_POINT, MIN_POINT),0.5))
								.serviceDuration(SERVICE_DURATION)
								.neededCapacity(1)
								.buildDTO());
						simulator.register(nfs);

						for (int i = 0; i < NUM_CUSTOMERS; i++) {
							FoodElement nfe = new FoodElement(
									Parcel.builder(foodPosition,
											MapUtil.rescale(Point.diff(MAX_POINT, MIN_POINT),0.5))
									.serviceDuration(SERVICE_DURATION)
									.neededCapacity(1)
									.buildDTO(), Math.random() * 2 + 1);
							simulator.register(nfe);
							nfs.putElement(nfe);

						}
						register(nfs);
					}

					// 1: check starving and empty food sources

					for(FoodSource source : new ArrayList<FoodSource>(SOURCES)) {
						if(source.isExpired()) {
							SOURCES.remove(source);
						}
					}

					// 2: calculate gradient fields
					for(Ant ant : ANTS) {
						Point resultingVector = new Point(0, 0);
						for(Ant other : ANTS) {
							resultingVector = MapUtil.addPoints(resultingVector, repulsiveField(ant, other));
						}

						for(FoodSource food : SOURCES) {
							resultingVector = MapUtil.addPoints(resultingVector, attractiveField(ant, food));
						}
						resultingVector = MapUtil.normalize(resultingVector);
						if(((Double) resultingVector.x).isNaN())
							resultingVector = new Point(0,0);
//							System.out.println("NaN");
						GRADIENT_VECTORS.put(ant, resultingVector);
					}

					// 3: clean dropped food elements
					for(FoodElement foodElement : DROPPED_FOOD_ELEMENTS.keySet()) {
						DroppedFoodSource nfs = new DroppedFoodSource(Parcel.builder(DROPPED_FOOD_ELEMENTS.get(foodElement),
								MapUtil.rescale(Point.diff(MAX_POINT, MIN_POINT),0.5))
						.serviceDuration(SERVICE_DURATION)
						.neededCapacity(1)
						.buildDTO(), foodElement);
//						simulator.unregister(foodElement);
						register(nfs);
						simulator.register(nfs);
					}
					DROPPED_FOOD_ELEMENTS.clear();
				}
			}

			@Override
			public void afterTick(TimeLapse timeLapse) {}
		});
	}

	static View.Builder createGui(
			boolean testing,
			@Nullable Display display,
			@Nullable Monitor m,
			@Nullable Listener list) {


		View.Builder view = View.builder()
				.with(PlaneRoadModelRenderer.builder())
				.with(RoadUserRenderer.builder()
						.withImageAssociation(
								TaxiBase.class, "/graphics/perspective/tall-building-64.png")
						.withImageAssociation(
								Ant.class, "/graphics/perspective/gas-truck-32.png") //replace
						//Taxi.class, "/src/main/resources/Tiger-PNG-Image.png")
						.withImageAssociation(
								FoodSource.class, "/graphics/flat/person-red-32.png")
						.withImageAssociation(
								DroppedFoodSource.class, "/graphics/flat/person-red-32.png"))		//picture food source
				.with(PDPModelRenderer.builder())
				.withTitleAppendix("Taxi Demo");

		if (testing) {
			view = view.withAutoClose()
					.withAutoPlay()
					.withSimulatorEndTime(TEST_STOP_TIME)
					.withSpeedUp(TEST_SPEED_UP);
		} else if (m != null && list != null && display != null) {
			view = view.withMonitor(m)
					.withSpeedUp(SPEED_UP)
					.withResolution(m.getClientArea().width, m.getClientArea().height)
					.withDisplay(display)
					.withCallback(list)
					.withAsync()
					.withAutoPlay()
					.withAutoClose();
		}
		return view;
	}

	// load the graph file
	static Graph<MultiAttributeData> loadGraph(String name) {
		try {
			if (GRAPH_CACHE.containsKey(name)) {
				return GRAPH_CACHE.get(name);
			}
			final Graph<MultiAttributeData> g = DotGraphIO
					.getMultiAttributeGraphIO(
							Filters.selfCycleFilter())
					.read(
							Environment.class.getResourceAsStream(name));

			GRAPH_CACHE.put(name, g);
			return g;
		} catch (final FileNotFoundException e) {
			throw new IllegalStateException(e);
		} catch (final IOException e) {
			throw new IllegalStateException(e);
		}
	}


	/**
	 * Gradient Field methods
	 */

	private static Point repulsiveField(Ant ant, Ant other) {
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

	private static Point attractiveField(Ant ant, FoodSource food) {
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

	public static List<Ant> getAnts() {
		return ANTS;
	}

	public static void register(Ant ant) {
		ANTS.add(ant);
		GRADIENT_VECTORS.put(ant, ant.getStartPosition()); //Not sure about this! :S
	}

	public static void register(Colony colony) {
		COLONY = colony;
	}

	public static void register(FoodSource source) {
		SOURCES.add(source);
	}

	public static FoodElement pickup(FoodSource source) {
		/*get().sources.remove(source);*/

		FoodElement food = source.pickup();
		if(source.getNumberElements() == 0) {
			SOURCES.remove(source);
		}
		return food;
	}

	public static ArrayList<FoodSource> getFoodSources() {
		return SOURCES;
	}

	public static FoodSource getFoodFromVisual(Ant ant) {
		return nearestVisibleFood(ant);
	}

	public static FoodSource nearestVisibleFood(Ant ant) {
		// develop method for seeing nearby food
		double shortestDist = 99999999;
		FoodSource nearestFood = null;
		for(FoodSource foodSource : SOURCES) {
			double dist = Point.distance(foodSource.getPickupLocation(), ant.getPosition().get());
			if(dist < Ant.VISUAL_RANGE && dist < shortestDist) {
				shortestDist = dist;
				nearestFood = foodSource;
			}
		}
		return nearestFood;
	}

	public static Point getGradientField(Ant ant) {
		return GRADIENT_VECTORS.get(ant);
	}

	public static void notifyDelivery() {
		SUCCESSFUL_DELIVERIES ++;
	}

	public static int getDeliveryCount() {
		return SUCCESSFUL_DELIVERIES;
	}

	// I'm wondering if the ant can directly ask to the environment, "Hey given the colony position!"
	public static Point getColonyPosition() {
		return COLONY.getPosition();
	}

	public static void dropFood(FoodElement el, Point position) {
		DROPPED_FOOD_ELEMENTS.put(el,position);
	}

	/**
	 * A customer with very permissive time windows.
	 */
	static class Customer extends Parcel {
		Customer(ParcelDTO dto) {
			super(dto);
		}

		@Override
		public void initRoadPDP(RoadModel pRoadModel, PDPModel pPdpModel) {}
	}



	// currently has no function



}
