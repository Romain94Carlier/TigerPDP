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
/*something*/

import com.github.rinde.rinsim.core.model.comm.CommDevice;
import com.github.rinde.rinsim.core.model.comm.CommDeviceBuilder;
import com.github.rinde.rinsim.core.model.comm.CommUser;
import com.github.rinde.rinsim.core.model.pdp.PDPModel;
import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.core.model.pdp.Vehicle;
import com.github.rinde.rinsim.core.model.pdp.VehicleDTO;
import com.github.rinde.rinsim.core.model.road.MoveProgress;
import com.github.rinde.rinsim.core.model.road.RoadModel;
import com.github.rinde.rinsim.core.model.road.RoadModels;
import com.github.rinde.rinsim.core.model.time.TimeLapse;
import com.github.rinde.rinsim.geom.Point;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

/**
 * Implementation of a very simple taxi agent. It moves to the closest customer,
 * picks it up, then delivers it, repeat.
 *
 * @author Rinde van Lon
 */
class Ant extends Vehicle implements CommUser {

	public static final double VISUAL_RANGE = 2.5d;	//debug
	private final double RESTING_RATE;	//tune
	private static final double SPEED = 1000d;
	private final double maxEnergy;	//tune
	private Optional<Parcel> curr;
	private double energy;
	private boolean resting;
	private Strategy energyStrategy;

	Optional<CommDevice> device;
	Optional<Point> destination;

	Ant(Point startPosition, int capacity, boolean bold, boolean dynamic, double maxEnergy) {
		super(VehicleDTO.builder()
				.capacity(capacity)
				.startPosition(startPosition)
				.speed(SPEED)
				.build());
		curr = Optional.absent();
		//this.getClass().getResourceAsStream("/src/main/resources/69_tiger.jpg");
		energyStrategy = new Strategy(bold, dynamic);
		energy = maxEnergy;
		this.maxEnergy = maxEnergy;
		RESTING_RATE = 0.00000003*maxEnergy/50;
	}

	@Override
	public void afterTick(TimeLapse timeLapse) {}

	//strategy for gas refilling and passenger acceptance

	@Override
	protected void tickImpl(TimeLapse time) {
		final RoadModel rm = getRoadModel();
		final PDPModel pm = getPDPModel();

		destination = Optional.absent();
		if (!time.hasTimeLeft()) {
			return;
		}		//first check energy before hunting
		
		boolean inCargo = false;
		
		if (curr.isPresent())
			inCargo = pm.containerContains(this, curr.get());
			
		if(wantToRest(inCargo)) {
//			if(pm.containerContains(this, curr.get())) {
//				pm.drop(this, curr.get(), time);
//			}
			
			Point pos1 = getPosition().get();
			MoveProgress mp = rm.moveTo(this, Environment.getColonyPosition(), time);
			Point pos2 = getPosition().get();
			double distance = Point.distance(pos1,pos2);
			energy -= distance;
			long remaining = time.getTime() - mp.time().getValue();
			if (rm.getPosition(this).equals(Environment.getColonyPosition())) {
				if(curr.isPresent() && pm.containerContains(this, curr.get())){
					pm.deliver(this, curr.get(), time);
					Environment.notifyDelivery();
				}
				rest(remaining);
				curr = Optional.absent();
			}
			
		}
		else {	//hunt
			if (!curr.isPresent()) {

				//curr = Optional.fromNullable(RoadModels.findClosestObject(
				//rm.getPosition(this), rm, Parcel.class));
				//precheck: (alive) prey within half tick distance
				//if so: curr becomes the detected prey and we moveTo(curr)
				// if not: ask a gradient vector and move or moveTo
				FoodSource seen = Environment.getFoodFromVisual(this);
				if(seen instanceof DroppedFoodSource)
					System.out.println("seen dropped food source");
				curr = Optional.fromNullable((Parcel) seen);
			}

			if(!curr.isPresent()) {	//we dont see food
				Point gradientVector = MapUtil.rescale(Environment.getGradientField(this), 1);
				//System.out.println("-> we dont see food");
				Point destinationPoint = MapUtil.addPoints(gradientVector, getPosition().get());
				destination = Optional.fromNullable(redirectInBounds(destinationPoint, rm));
				Point pos1 = getPosition().get();
				rm.moveTo(this, destination.get(), time);
				Point pos2 = getPosition().get();
				double distance = Point.distance(pos1,pos2);
				energy -= distance;
			}
			if (curr.isPresent()) {
				//final boolean inCargo = pm.containerContains(this, curr.get());
				// sanity check: if it is not in our cargo AND it is also not on the
				// RoadModel, we cannot go to curr anymore.
				// TODO: remove from taxibase?
				if (!inCargo && !rm.containsObject(curr.get())) { //sanity/consistency check
					curr = Optional.absent();
				} else if (inCargo) {
					// if it is in cargo, go to its destination
					Point pos1 = getPosition().get();
					rm.moveTo(this, curr.get().getDeliveryLocation(), time);
					Point pos2 = getPosition().get();
					double distance = Point.distance(pos1,pos2);
					energy -= distance*2;
					boolean dropping = false;
					if(energy <= 0){
						//curr = Optional.absent();
						Parcel it = curr.get();
						///Probably this is not the best way to do it. It should happen in the environment
						pm.drop(this, it, time); 
						Environment.dropFood((FoodElement) it, this.getPosition().get());
						System.out.println("Drop food element");
						dropping = true;
					}
					
					if (!dropping && curr.isPresent() && rm.getPosition(this).equals(curr.get().getDeliveryLocation())) {
						// deliver when we arrive
						pm.deliver(this, curr.get(), time);
						Environment.notifyDelivery();
					}
				} else {
					// it is still available, go there as fast as possible
					Point pos1 = getPosition().get();
					rm.moveTo(this, curr.get(), time);
					Point pos2 = getPosition().get();
					double distance = Point.distance(pos1,pos2);
					energy -= distance;
					if (rm.equalPosition(this, curr.get())) {
						// pickup food element
						curr = Optional.fromNullable((Parcel) Environment.pickup((FoodSource) curr.get()));	// ugly
						//if(curr.isPresent())
						try {
							energy -= ((FoodElement) curr.get()).getFixedCost();
							pm.pickup(this, curr.get(), time);
						} catch (ClassCastException cce) {
							curr = Optional.absent();
							System.out.println("cce");
						} catch (IllegalStateException ise){
							System.out.println("Ant reached a depleted food source!");
						} catch (IllegalArgumentException ise){
							ise.printStackTrace();
//							throw ise;
						}
					}
				}
			}
		}
	}

	private boolean wantToRest(boolean inCargo) {
		if(resting)
			return true;
		boolean result = false;
		if(energyStrategy.bold == false && energyStrategy.dynamic == false){
			result = energy < maxEnergy/10;
		}
		if(energyStrategy.bold == true && energyStrategy.dynamic == false){
			result = energy < maxEnergy/20;
		}
		if(energyStrategy.bold == false && energyStrategy.dynamic == true){
			if(curr.isPresent()){
				if(!inCargo){
					double distance = Point.distance(this.getPosition().get(), curr.get().getPickupLocation())
							+ Point.distance(Environment.getColonyPosition(), curr.get().getPickupLocation()) * 2; //*********************************** 
					if(distance > (energy + 1)){
						result = true;
					}
				}else{		//food in cargo: determine if we expect to need resting when we deliver
					if(energy < 3){ //We need a good number here
						result = true;
					}
				}
			}else {			//roaming the gradient field without parcel
				if (Point.distance(this.getPosition().get(), Environment.getColonyPosition()) > energy + 0.2){ //We need a good number here
					result = true;
				}
			}
		}
		if(energyStrategy.bold == true && energyStrategy.dynamic == true){
			if(curr.isPresent()){
				if(!inCargo){
					double distance = Point.distance(this.getPosition().get(), curr.get().getPickupLocation())
							+ Point.distance(Environment.getColonyPosition(), curr.get().getPickupLocation()) * 2;
					if(distance > (energy + 2)){
						result = true;
						//System.out.println("true because unlikely ant will be able to carry the food it saw back to colony");
					}
				}else{		//food in cargo: determine if we expect to need resting when we deliver
					if(energy < 0){ //We need a good number here, probably doesn't matter much
						result = true;
						System.out.println("true because carrying emptied the energy down to 1");
					}
				}
			}else {			//roaming the gradient field without parcel
				if (Point.distance(this.getPosition().get(), Environment.getColonyPosition()) > energy + 0.2){ //We need a good number here
					result = true;
//					System.out.println("true because ant can barely reach the colony");
				}
			}
		}
		//System.out.println("want to rest: "+result);
		return result;
	}

	private void rest(long time) {
		resting = true;
		double rate;
		if(energy < 0)
			rate = RESTING_RATE / 5;
		else rate = RESTING_RATE;
		energy += time * rate;	// tune
		if(energy >= maxEnergy) {
			energy = maxEnergy;
			resting = false;
		}
	}

	private Point redirectInBounds(Point point, RoadModel rm) {
		ImmutableList<Point> bounds = rm.getBounds();
		if(point.x < bounds.get(0).x)
			point = new Point(0,point.y);
		if(point.x > bounds.get(1).x)
			point = new Point(10,point.y);
		if(point.y < bounds.get(0).y)
			point = new Point(point.x,0);
		if(point.y > bounds.get(1).y)
			point = new Point(point.x,10);
		return point;
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

	public boolean isTaken() {		//TODO: private? all interactions through communication eventually
		return ! (this.curr.equals(Optional.<Parcel>absent()));
	}

	public double getEnergy() {
		return this.energy;
	}
	
	public class Strategy {
		boolean bold;
		boolean dynamic;
		Strategy(boolean bold, boolean dynamic){
			this.bold = bold;
			this.dynamic = dynamic;
		}
	}
}
