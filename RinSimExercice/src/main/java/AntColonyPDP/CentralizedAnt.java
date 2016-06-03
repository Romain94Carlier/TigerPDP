package AntColonyPDP;
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
import com.github.rinde.rinsim.core.model.road.RoadModel;
import com.github.rinde.rinsim.core.model.time.TimeLapse;
import com.github.rinde.rinsim.geom.Point;
import com.google.common.base.Optional;

/**
 * Implementation of a very simple taxi agent. It moves to the closest customer,
 * picks it up, then delivers it, repeat.
 *
 * @author Rinde van Lon
 */
class CentralizedAnt extends Ant {
	
	private boolean grounded = false;

	public CentralizedAnt(Point startPosition, int capacity) {
		super(startPosition, capacity);
		curr = Optional.absent();
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
			Point pos1 = getPosition().get();
			rm.moveTo(this, Environment.getNearestColony(this).getPosition(), time);
			Point pos2 = getPosition().get();
			double distance = Point.distance(pos1,pos2);
			decreaseEnergy(distance);
			if (rm.getPosition(this).equals(Environment.getNearestColony(this).getPosition())) {
				if(curr.isPresent() && pm.containerContains(this, curr.get())){
					throw new IllegalStateException("Centralized ant resting but not empty");
				}
				rest(time.getTimeLeft());
			}
			
		}
		if (!curr.isPresent()) {
			curr = Optional.fromNullable(Environment.getAssignment(this));		//adapt for better refresh			
		}

		if (curr.isPresent()) {
			if (!inCargo && !rm.containsObject(curr.get())) { //sanity/consistency check
				curr = Optional.absent();
			} else if (inCargo) {
				// if it is in cargo, go to its destination
				Point pos1 = getPosition().get();
				rm.moveTo(this, curr.get().getDeliveryLocation(), time);
				Point pos2 = getPosition().get();
				double distance = Point.distance(pos1,pos2);
				decreaseEnergy(distance*2);
				if (curr.isPresent() && rm.getPosition(this).equals(curr.get().getDeliveryLocation())) {
					// deliver when we arrive
					if(getEnergy() <0)
						throw new IllegalStateException("<0 energy, carrying "+curr.get()+" energy: "+getEnergy()+" position: "+getPosition());
					pm.deliver(this, curr.get(), time);
					Environment.notifyDelivery();
				}
			} else {
				// it is still available, go there as fast as possible
				Point pos1 = getPosition().get();
				rm.moveTo(this, curr.get(), time);
				Point pos2 = getPosition().get();
				double distance = Point.distance(pos1,pos2);
				decreaseEnergy(distance);
				if (rm.equalPosition(this, curr.get()) && Environment.canDeliver(this, (FoodSource) curr.get())) {
					// pickup food element from food source
					curr = Optional.fromNullable((Parcel) Environment.pickup((FoodSource) curr.get()));	// ugly
					try {
						decreaseEnergy(((FoodElement) curr.get()).getFixedCost());
						pm.pickup(this, curr.get(), time);
					} catch (ClassCastException cce) {
						curr = Optional.absent();
						System.out.println("cce");
					} catch (IllegalStateException ise){
						System.out.println("Ant reached a depleted food source!");
					} catch (IllegalArgumentException ise){
						ise.printStackTrace();
					}
				}
			}
		}
	}

	private boolean wantToRest(boolean inCargo) {
		if(isResting())
			return true;
		return grounded;
	}

	protected void rest(long time) {
		super.rest(time);
		grounded = false;
	}
	
	public void groundAnt() {
		grounded = true;
	}
}
