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
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.apache.commons.math3.random.RandomGenerator;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Monitor;

import com.github.rinde.rinsim.core.Simulator;
import com.github.rinde.rinsim.core.model.pdp.Container;
import com.github.rinde.rinsim.core.model.pdp.DefaultPDPModel;
import com.github.rinde.rinsim.core.model.pdp.Depot;
import com.github.rinde.rinsim.core.model.pdp.PDPModel;
import com.github.rinde.rinsim.core.model.pdp.PDPModel.ParcelState;
import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.core.model.pdp.ParcelDTO;
import com.github.rinde.rinsim.core.model.pdp.Vehicle;
import com.github.rinde.rinsim.core.model.road.RoadModel;
import com.github.rinde.rinsim.core.model.road.RoadModelBuilders;
import com.github.rinde.rinsim.core.model.road.RoadUser;
import com.github.rinde.rinsim.core.model.time.TickListener;
import com.github.rinde.rinsim.core.model.time.TimeLapse;
import com.github.rinde.rinsim.event.Listener;

//import com.github.rinde.rinsim.examples.core.taxi.Taxi;
//import com.github.rinde.rinsim.examples.core.taxi.TaxiExample.Customer;
//import com.github.rinde.rinsim.examples.core.taxi.TaxiExample.TaxiBase;

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

import TigerPDP.TaxiBase.Pair;

/**
 * Example showing a fleet of taxis that have to pickup and transport customers
 * around the city of Leuven.
 * <p>
 * If this class is run on MacOS it might be necessary to use
 * -XstartOnFirstThread as a VM argument.
 * @author Rinde van Lon
 */
public final class Environment {

	// Independent variables
	public static final int MAP_SCALE = 100;
	public static final boolean CENTRALIZED = false;
	private static final boolean TESTING = true;
	static final boolean BOLD_AGENTS = false;		//try out different strategies
	static final boolean DYNAMIC_AGENTS = true;
	private static final int NUM_ANTS = 200;

	private static final int NUM_COLONIES = (NUM_ANTS-1)/4+1;
	private static final int ANT_CAPACITY = 1;
	private static final double GF_THRESHOLD = MAP_SCALE*.4;
	static final double VEHICLE_SPEED_KMH = 5d*10;

	private static final int NUM_FOOD_SOURCE = NUM_ANTS/2;
	private static final int FOOD_SOURCE_SIZE = 100/MAP_SCALE;
	private static final long SERVICE_DURATION = 60000;
	private static final double NEW_FOOD_SOURCE_PROB = .007*NUM_ANTS/5/FOOD_SOURCE_SIZE;
	private static final int MAX_SOURCES = NUM_ANTS; //Maximum amount of food sources at the same time
	// time in ms

	private static HashMap<CentralizedAnt,FoodSource> assignments = new HashMap<CentralizedAnt,FoodSource>();

	//gui/simulator settings settings
	private static final Map<String, Graph<MultiAttributeData>> GRAPH_CACHE =
			newHashMap();
	private static final int SPEED_UP = 4;
	private static final long TEST_STOP_TIME = 21/3*200 * 60 * 1000;
	private static final int TEST_SPEED_UP = 64;

	//plane params


	static final Point MIN_POINT = new Point(0, 0);
	static final Point MAX_POINT = new Point(MAP_SCALE, MAP_SCALE);

	//Gradient field parameters
	private static ArrayList<Colony> COLONIES = new ArrayList<Colony>();
	private static ArrayList<GFAnt> ANTS = new ArrayList<GFAnt>();
	private static ArrayList<CentralizedAnt> CENTRALIZED_ANTS = new ArrayList<CentralizedAnt>();
	private static ArrayList<FoodSource> SOURCES = new ArrayList<FoodSource>();
	private static HashMap<FoodElement, List<Point>> DROPPED_FOOD_ELEMENTS = new HashMap<FoodElement,List<Point>>();
	private static ArrayList<FoodElement> destroyedFoods = new ArrayList<FoodElement>();
	private static HashMap<Vehicle,Point> GRADIENT_VECTORS = new HashMap<Vehicle,Point>();
	private static int SUCCESSFUL_DELIVERIES = 0;
	private static int EXPIRED_SOURCES = 0;
	private static int EXPIRED_ELEMENTS = 0;


	private Environment() {}

	/**
	 * Starts the {@link TaxiExample}.
	 * @param args The first option may optionally indicate the end time of the
	 *          simulation.
	 */
	public static void main(@Nullable String[] args) {
		final long endTime = args != null && args.length >= 1 ? Long
				.parseLong(args[0]) : Long.MAX_VALUE;

				run(TESTING, endTime, "gradient field", null /* new Display() */, null, null);
	}

	/**
	 * Run the example.
	 * @param testing If <code>true</code> enables the test mode.
	 */
	public static void run(boolean testing) {
		run(testing, Long.MAX_VALUE, "gradient field", null, null, null);
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
			String setup,
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
		if (CENTRALIZED)
			registerCentralizedSimulator(endTime, simulator, rng, roadModel);
		else
			registerGradientFieldSimulator(endTime, simulator, rng, roadModel);
		simulator.start();

		return simulator;
	}

	private static void registerCentralizedSimulator(final long endTime,
			final Simulator simulator, final RandomGenerator rng,
			final RoadModel roadModel) {

		for (int i = 0; i < NUM_COLONIES; i++) {
			Colony colony = new Colony (roadModel.getRandomPosition(rng), 0);
			simulator.register(colony); 
			//Environment.register(colony);
			register(colony);
		}
		for (int i = 0; i < NUM_ANTS; i++) {
			CentralizedAnt nca = new CentralizedAnt(roadModel.getRandomPosition(rng), ANT_CAPACITY);
			simulator.register(nca);
			register(nca);
		}

		for (int i = 0; i < NUM_FOOD_SOURCE; i++) {
			generateFoodSource(simulator, rng, roadModel);
		}

		simulator.addTickListener(new TickListener() {
			@Override
			public void tick(TimeLapse time) {

				if (time.getStartTime() > endTime) {
					simulator.stop();
				} else 
				{

					if (rng.nextDouble() < NEW_FOOD_SOURCE_PROB*(MAX_SOURCES-SOURCES.size())/MAX_SOURCES){ //&& SOURCES.size() < MAX_SOURCES) {
						generateFoodSource(simulator, rng, roadModel);
					}

					// 1: check starving and empty food sources

					for(FoodSource source : new ArrayList<FoodSource>(SOURCES)) {
						if(source.isExpired()) {
							SOURCES.remove(source);
						}
					}

					assignments = new HashMap<CentralizedAnt,FoodSource>();

					//1. get the positions (via comm or references)
					HashMap<FoodSource,Point> fsPositions = new HashMap<FoodSource,Point>();
					for(FoodSource fs : SOURCES)
						fsPositions.put(fs,fs.getPickupLocation());
					HashMap<CentralizedAnt,Point> caPositions = new HashMap<CentralizedAnt,Point>();
					for(CentralizedAnt ca : CENTRALIZED_ANTS)
						caPositions.put(ca, ca.getPosition().get());

					//2. extract closest pair and find next
					ArrayList<CentralizedAnt> remainingAnts = new ArrayList<CentralizedAnt>(CENTRALIZED_ANTS);
					for(CentralizedAnt ant : CENTRALIZED_ANTS){
						if(ant.isTaken())
							remainingAnts.remove(ant);
					}
					ArrayList<FoodSource> remainingSources = new ArrayList<FoodSource>(SOURCES);
					Map<Pair,Double> shortestFound = new HashMap<Pair,Double>();

					while(!remainingSources.isEmpty() && !remainingAnts.isEmpty()) {
						for(FoodSource fs : remainingSources) {
							Point pos = fsPositions.get(fs);
							Map<Pair,Double> distances = new HashMap<Pair,Double>();
							for(CentralizedAnt ca : remainingAnts) {
								Point antPos = caPositions.get(ca);
								distances.put(new Pair(ca, fs), Point.distance(antPos, pos));
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
								//System.out.println("found distance: "+ entry.getValue());
							}
						}
						//we found and now use the absolute minimal distance pair found
						//if the ant can reach it!!
						if(canDeliver(absoluteShortest.v,absoluteShortest.c)) {
							assignments.put(absoluteShortest.v, absoluteShortest.c);
							remainingSources.remove(absoluteShortest.c);
						}
						else
							absoluteShortest.v.groundAnt();
						remainingAnts.remove(absoluteShortest.v);
						shortestFound = new HashMap<Pair,Double>();


					}
					printAssignments(assignments);
				}
			}

			@Override
			public void afterTick(TimeLapse timeLapse) {
				performanceAssessment(simulator, roadModel);
			}
		});
	}

	private static void registerGradientFieldSimulator(final long endTime,
			final Simulator simulator, final RandomGenerator rng,
			final RoadModel roadModel) {

		//Environment env = new Environment();
		//Environment.set(env);
		//simulator.register(env);

		for (int i = 0; i < NUM_COLONIES; i++) {
			Colony colony = new Colony (roadModel.getRandomPosition(rng), 0);
			simulator.register(colony); 
			//Environment.register(colony);
			register(colony);
		}

		for (int i = 0; i < NUM_ANTS; i++) {
			GFAnt newAnt = new GFAnt(roadModel.getRandomPosition(rng), ANT_CAPACITY, BOLD_AGENTS, DYNAMIC_AGENTS);
			simulator.register(newAnt);
			//Environment.register(newAnt);
			register(newAnt);
		}

		for (int i = 0; i < NUM_FOOD_SOURCE; i++) {
			generateFoodSource(simulator, rng, roadModel);
		}

		simulator.addTickListener(new TickListener() {
			@Override
			public void tick(TimeLapse time) {

				if (time.getStartTime() > endTime) {
					simulator.stop();
				} else 
				{

					if (rng.nextDouble() < NEW_FOOD_SOURCE_PROB && SOURCES.size() < MAX_SOURCES) {
						generateFoodSource(simulator, rng, roadModel);
					}

					// 1: check starving and empty food sources

					for(FoodSource source : new ArrayList<FoodSource>(SOURCES)) {
						if(source.isExpired()) {
							SOURCES.remove(source);
						}
					}

					// 2: calculate gradient fields
					for(GFAnt ant : ANTS) {
						Point resultingVector = new Point(0, 0);
						if(!ant.wantToRest()){ // if ant doesn't want to rest calculate normal gradient field
							for(GFAnt other : ANTS) {
								resultingVector = MapUtil.addPoints(resultingVector, repulsiveField(ant, other));
							}

							for(FoodSource food : SOURCES) {
								resultingVector = MapUtil.addPoints(resultingVector, attractiveField(ant, food));
							}
						}else{//otherwise calculate to reach the colony
							for(Colony colony : COLONIES) {
								resultingVector = MapUtil.addPoints(resultingVector, attractiveField(ant, colony));
							}
							for(GFAnt other : ANTS) {
								if(other.isResting())
									resultingVector = MapUtil.addPoints(resultingVector, repulsiveField(ant, other));
							}	
						}

						resultingVector = MapUtil.normalize(resultingVector);
						if(((Double) resultingVector.x).isNaN())
							resultingVector = new Point(0,0);
						//							System.out.println("NaN");
						GRADIENT_VECTORS.put(ant, resultingVector);
					}

					for(FoodElement fe : new ArrayList<>(destroyedFoods)) {
						if(roadModel.containsObject(fe)) {
							//System.out.println("contained destroyed food");
							roadModel.removeObject(fe);
							destroyedFoods.remove(fe);
						}
					}

					// 3: clean dropped food elements
					//					for(FoodElement foodElement : DROPPED_FOOD_ELEMENTS.keySet()) {
					//						DroppedFoodSource nfs = new DroppedFoodSource(Parcel.builder(DROPPED_FOOD_ELEMENTS.get(foodElement),
					//								MapUtil.rescale(Point.diff(MAX_POINT, MIN_POINT),0.5))
					//								.serviceDuration(SERVICE_DURATION)
					//								.neededCapacity(1)
					//								.buildDTO(), foodElement);
					//						simulator.unregister(foodElement);
					//						register(nfs);
					//						simulator.register(nfs);

					//					}
					//					if(roadModel.getObjects().size() == 8)
					//						System.out.println("8 road users");
					
					for(FoodElement foodElement : new HashSet<FoodElement>(DROPPED_FOOD_ELEMENTS.keySet())) {
						//						simulator.unregister(foodElement);

						Point usedPosition = DROPPED_FOOD_ELEMENTS.get(foodElement).get(0);
						Point deliveryPosition = DROPPED_FOOD_ELEMENTS.get(foodElement).get(1);
						//System.out.println("used position: "+usedPosition);
						FoodElement nfe = new FoodElement(
								Parcel.builder(usedPosition,
										deliveryPosition)
								.serviceDuration(SERVICE_DURATION)
								.neededCapacity(1)
								.buildDTO(), Math.random() * 2 + 1);
						simulator.register(nfe);
						//System.out.println("registered nfe "+nfe.toString());
						//						register(nfe);
						destroyedFoods.add(foodElement);
						//foodElement.destroy();
//						if(!roadModel.containsObject(destroyedFood))
//							System.out.println("not contains destroyed food");
//						System.out.println("destroyed fe "+foodElement.toString());
						//						roadModel.removeObject(foodElement);

						//foodElement.setLocation(DROPPED_FOOD_ELEMENTS.get(foodElement));

						DroppedFoodSource nfs = new DroppedFoodSource(Parcel.builder(usedPosition,
								deliveryPosition)
								.serviceDuration(SERVICE_DURATION)
								.neededCapacity(1)
								.buildDTO(), nfe);

						register(nfs);
						simulator.register(nfs);
						//System.out.println("registered nfs "+nfs.toString());
						//simulator.unregister(foodElement);
						//						boolean modified = simulator.getModelProvider().getModel(DefaultPDPModel.class).unregister(foodElement);
						//						
						//						modified = modified && roadModel.unregister(foodElement);
						ParcelState state = simulator.getModelProvider().getModel(DefaultPDPModel.class).getParcelState(foodElement);
						//System.out.println("state: "+state) ;
						//						if(!modified)	 
						//							System.out.println("inconsistent dpmodel");
						//						else
						DROPPED_FOOD_ELEMENTS.remove(foodElement);
					}
					//					DROPPED_FOOD_ELEMENTS.clear();

				}
			}

			@Override
			public void afterTick(TimeLapse timeLapse) {

				// only at simulator minutes
				performanceAssessment(simulator, roadModel);
			}
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
								Colony.class, "/ant_colony.png")
						.withImageAssociation(
								GFAnt.class, "/small_ant.png")
						.withImageAssociation(
								CentralizedAnt.class, "/small_ant.png")
						.withImageAssociation(
								FoodSource.class, "/tiny_wheat.png")
						.withImageAssociation(
								DroppedFoodSource.class, "/tiny_wheat.png")
						).with(PDPModelRenderer.builder())
				.withTitleAppendix("Ant colony Demo");

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


	private static void generateFoodSource(final Simulator simulator, final RandomGenerator rng,
			final RoadModel roadModel) {
		Point foodPosition = roadModel.getRandomPosition(rng);
		Point deliveryLocation = generateDeliveryLocation();
		FoodSource nfs = new FoodSource(
				Parcel.builder(foodPosition,
						deliveryLocation)
				.serviceDuration(SERVICE_DURATION)
				.neededCapacity(ANT_CAPACITY)
				.buildDTO());
		simulator.register(nfs);

		for (int j = 0; j < FOOD_SOURCE_SIZE; j++) {
			FoodElement nfe = new FoodElement(
					Parcel.builder(foodPosition,
							deliveryLocation)
					.serviceDuration(SERVICE_DURATION)
					.neededCapacity(ANT_CAPACITY)
					.buildDTO(), Math.random() * 2 + 1);
			simulator.register(nfe);
			nfs.putElement(nfe);

		}
		register(nfs);
	}

	/**
	 * Gradient Field methods
	 */

	private static Point repulsiveField(GFAnt ant, GFAnt other) {
		if(ant.equals(other))
			return new Point(0,0);
		Point p1 = ant.getPosition().get();
		Point p2 = other.getPosition().get();
		double distance = Point.distance(p1, p2);
		if(distance == 0)
			return new Point(0,0);
		if(distance > GF_THRESHOLD)
			return new Point(0,0);
		Point result = Point.diff(p1, p2);	// debug: order for repulse/attract
		result = MapUtil.normalize(result);
		result = MapUtil.rescale(result, 1.6/distance/distance);
		return result;
	}

	private static Point attractiveField(GFAnt ant, FoodSource food) {
		Point p1 = ant.getPosition().get();
		Point p2 = food.getPosition();
		double distance = Point.distance(p1, p2);
		if(distance > GF_THRESHOLD)
			return new Point(0,0);
		Point result = Point.diff(p2, p1);	// debug: order
		result = MapUtil.normalize(result);
		result = MapUtil.rescale(result, 1/distance/distance);
		int remainingFoodModifier = food.getNumberElements(); 	// any other modifiers or does this suffice?
		//if(remainingFoodModifier == 0)
			//System.out.println("depleted food source");
		result = MapUtil.rescale(result, Math.log(remainingFoodModifier+1)+1);
		return result;
	}

	private static Point attractiveField(GFAnt ant, Colony food) {
		Point p1 = ant.getPosition().get();
		Point p2 = food.getPosition();
		double distance = Point.distance(p1, p2);
		//TODO: threshold
		Point result = Point.diff(p2, p1);	// debug: order
		result = MapUtil.normalize(result);
		result = MapUtil.rescale(result, 1/distance/distance);
		return result;
	}

	public static boolean canDeliver(CentralizedAnt v, FoodSource c) {
		double energy = v.getEnergy();
		Point foodPos = c.getPosition();
		double dist1 = Point.distance(v.getPosition().get(), foodPos);
		double dist2 = Point.distance(c.getDeliveryLocation(), foodPos);
		return (energy >= dist1 + 2* dist2 + c.getElementCost());
	}

	public static List<GFAnt> getAnts() {
		return ANTS;
	}

	private static void performanceAssessment(final Simulator simulator, final RoadModel roadModel) {
		if((simulator.getCurrentTime() % (3600000*20)) == 0 && simulator.getCurrentTime()>0) {
			System.out.println("expired food sources: "+getExpiredSourceCount()+", elements: "+getExpiredElementCount());
			System.out.println("succesfulDeliveries: "+ getDeliveryCount());
			double antCount = ((double) getDeliveryCount())/((double) NUM_ANTS);
			System.out.println("ant efficiency: "+antCount);
			System.out.println("per simulation minute: "+antCount/(simulator.getCurrentTime()/3600000));
			Map<RoadUser, Point> debug = roadModel.getObjectsAndPositions();
			for(RoadUser ru : new HashSet<RoadUser>(debug.keySet())){
				if(ru instanceof Ant)
					debug.remove(ru);
				/*
				if(ru instanceof CentralizedAnt)
					debug.remove(ru);
				 */
				else if(ru instanceof Colony)
					debug.remove(ru);
				//						else if(ru instanceof FoodSource && !(ru instanceof FoodElement))
				//							debug.remove(ru);
				else if(ru instanceof FoodElement && (simulator.getModelProvider().getModel(DefaultPDPModel.class).getParcelState((FoodElement) ru)).equals(ParcelState.DELIVERED))
					debug.remove(ru);
				else System.out.print("("+Math.round(((FoodSource) ru).getPickupLocation().x)+","+Math.round(((FoodSource) ru).getPickupLocation().y)+") ");
			}
		}
	}

	public static void register(GFAnt ant) {
		ANTS.add(ant);
		GRADIENT_VECTORS.put(ant, ant.getStartPosition());
	}

	public static void register(CentralizedAnt ant) {
		CENTRALIZED_ANTS.add(ant);
	}

	public static void register(Colony colony) {
		COLONIES.add(colony);
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

	public static FoodSource getFoodFromVisual(GFAnt ant) {
		return nearestVisibleFood(ant);
	}

	public static FoodSource nearestVisibleFood(GFAnt ant) {
		// develop method for seeing nearby food
		double shortestDist = 99999999;
		FoodSource nearestFood = null;
		for(FoodSource foodSource : SOURCES) {
			double dist = Point.distance(foodSource.getPosition(), ant.getPosition().get());
			if(dist < GFAnt.VISUAL_RANGE && dist < shortestDist) {
				shortestDist = dist;
				nearestFood = foodSource;
				//if(dist == 0.0)
					//System.out.println("on food element");
			}
		}
		return nearestFood;
	}

	public static Point getGradientField(GFAnt ant) {
		return GRADIENT_VECTORS.get(ant);
	}

	public static void notifyDelivery() {
		SUCCESSFUL_DELIVERIES ++;
	}

	public static void notifyExpiring(int elements) {
		EXPIRED_SOURCES ++;
		EXPIRED_ELEMENTS += elements;
	}

	private static int getExpiredSourceCount() {
		return EXPIRED_SOURCES;
	}

	private static int getExpiredElementCount() {
		return EXPIRED_ELEMENTS;
	}

	public static int getDeliveryCount() {
		return SUCCESSFUL_DELIVERIES;
	}

	// I'm wondering if the ant can directly ask to the environment, "Hey given the colony position!"
	public static Colony getNearestColony(Ant ant) {
		Colony closest = null;
		double dist = 99999;
		for(Colony colony : COLONIES) {
			double newDist = Point.distance(ant.getPosition().get(), colony.getPosition());
			if(closest == null || newDist<dist) {
				closest = colony;
				dist = newDist;
			}
		}
		return closest;
	}

	public static void dropFood(FoodElement el, List<Point> positions) {
		DROPPED_FOOD_ELEMENTS.put(el,positions);
	}

	public static void remove(FoodSource parcel) {
		SOURCES.remove(parcel);		
	}

	public static Parcel getAssignment(CentralizedAnt ca) {
		return assignments.get(ca);
	}

	private static void printAssignments(HashMap<CentralizedAnt,FoodSource> assignments) {
		for(Vehicle v : assignments.keySet()){
			Point taxipos = ((CentralizedAnt) v).getPosition().get();
			Point customerpos = assignments.get(v).getPickupLocation();
			//System.out.println("taxi pos: "+taxipos+" customer pos : "+customerpos+" distance :"+Point.distance(taxipos, customerpos));
		}
	}

	private static Point generateDeliveryLocation() {
		int index = (int) Math.floor(Math.random()*NUM_COLONIES);
		return COLONIES.get(index).getPosition();
	}

	static class Pair {
		CentralizedAnt v;
		FoodSource c;
		Pair(CentralizedAnt v, FoodSource c){
			this.v = v;
			this.c = c;
		}
	}

	public static boolean mayRest(Ant ant) {
		return getNearestColony(ant).occupyForResting(ant);
	}

	//	public static boolean mayRest(Ant ant, Colony colony) {
	//
	//		if(colony == null)
	//			return false;
	//
	//		return colony.occupyForResting(ant);
	//	}

	public static Colony getColonyFromVisual(GFAnt gfAnt) {
		// develop method for seeing nearby colony
		double shortestDist = 99999999; //not sure what it is for
		Colony nearestColony = null;
		for(Colony colony : COLONIES) {
			if(!colony.isOccupiedByOtherAnt(gfAnt)){
				double dist = Point.distance(colony.getPosition(), gfAnt.getPosition().get());
				if(dist < GFAnt.VISUAL_RANGE && dist < shortestDist) {
					shortestDist = dist;
					nearestColony = colony;
				}
			}
		}
		return nearestColony;
	}

}
