package TigerPDP;
/*
 * Copyright (C) 2011-2016 Rinde van Lon, iMinds-DistriNet, KU Leuven
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

import TigerPDP.Ant.Strategy;

/**
 * Implementation of a very simple taxi agent. It moves to the closest customer,
 * picks it up, then delivers it, repeat.
 *
 * @author Rinde van Lon
 */
class CentralizedAnt extends Vehicle {
	private final double RESTING_RATE;	//tune
	private static final double SPEED = 1000d;
	private final double maxEnergy;	//tune
	private Optional<Parcel> curr;
	private double energy;
	private boolean resting;
	private boolean grounded = false;

	CentralizedAnt(Point startPosition, int capacity, double maxEnergy) {
		super(VehicleDTO.builder()
				.capacity(capacity)
				.startPosition(startPosition)
				.speed(SPEED)
				.build());
		curr = Optional.absent();
		//this.getClass().getResourceAsStream("/src/main/resources/69_tiger.jpg");
		energy = maxEnergy;
		this.maxEnergy = maxEnergy;
		RESTING_RATE = 0.00000003*maxEnergy/50;
	}

	@Override
	public void afterTick(TimeLapse timeLapse) {}

	@Override
	protected void tickImpl(TimeLapse time) {
		final RoadModel rm = getRoadModel();
		final PDPModel pm = getPDPModel();
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
//					pm.deliver(this, curr.get(), time);
//					Environment.notifyDelivery();
					throw new IllegalStateException("Centralized ant resting but not empty");
				}
				rest(remaining);
//				curr = Optional.absent();
			}
			
		}
		if (!curr.isPresent()) {
			//curr = Optional.fromNullable(RoadModels.findClosestObject(
			//rm.getPosition(this), rm, Parcel.class));
			//precheck: (alive) prey within half tick distance
			//if so: curr becomes the detected prey and we moveTo(curr)
			// if not: ask a gradient vector and move or moveTo
			curr = Optional.fromNullable(Environment.getAssignment(this));		//adapt for better refresh			
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
				if (curr.isPresent() && rm.getPosition(this).equals(curr.get().getDeliveryLocation())) {
					// deliver when we arrive
					if(energy <0)
						throw new IllegalStateException("<0 energy");
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
						//						throw ise;
					}
				}
			}
		}
	}

	public Optional<Point> getPosition() {
		return Optional.of(getRoadModel().getPosition(this));
	}
	
	private boolean wantToRest(boolean inCargo) {
		if(resting)
			return true;
		return grounded;
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
			grounded = false;
		}
	}
	
	public boolean isResting() {
		return resting;
	}
	
	public void groundAnt() {
		grounded = true;
	}
	
	public boolean isTaken() {		//TODO: private? all interactions through communication eventually
		return ! (this.curr.equals(Optional.<Parcel>absent()));
	}
	
	public double getEnergy() {
		return this.energy;
	}
}
