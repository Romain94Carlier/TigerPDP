package AntColonyPDP;
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

import java.util.ArrayList;
import java.util.List;

import com.github.rinde.rinsim.core.model.pdp.PDPModel;
import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.core.model.pdp.PDPModel.VehicleState;
import com.github.rinde.rinsim.core.model.road.RoadModel;
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
class GFAnt extends Ant {

	public static final double VISUAL_RANGE = 2.5d;
	private Optional<Parcel> curr;
	private Strategy energyStrategy;

	Optional<Point> destination;
	private boolean wantToRest;

	public GFAnt(Point startPosition, int capacity, boolean bold, boolean dynamic) {
		super(startPosition, capacity);
		curr = Optional.absent();
		energyStrategy = new Strategy(bold, dynamic);
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

		wantToRest = wantToRest(inCargo);

		if(wantToRest) {

			Colony colony = Environment.getColonyFromVisual(this);

			if(colony == null){		//find a colony and forget about the food
				Point gradientVector = MapUtil.rescale(Environment.getGradientField(this), 1);
				Point destinationPoint = MapUtil.addPoints(gradientVector, getPosition().get());
				destination = Optional.fromNullable(redirectInBounds(destinationPoint, rm));
				Point pos1 = getPosition().get();
				rm.moveTo(this, destination.get(), time);
				Point pos2 = getPosition().get();
				double distance = Point.distance(pos1,pos2);
				decreaseEnergy(distance);
			}else{		// we found a colony, keep going until reach it
				Point pos1 = getPosition().get();
				rm.moveTo(this, colony, time);
				Point pos2 = getPosition().get();
				double distance = Point.distance(pos1,pos2);
				decreaseEnergy(distance);

				if(getEnergy() <= 0 && inCargo){
					Parcel it = curr.get();
					pm.drop(this, it, time); 
					List<Point> positions = new ArrayList<Point>();
					positions.add(0, this.getPosition().get());
					positions.add(1, it.getDeliveryLocation());
					Environment.dropFood((FoodElement) it, positions);
				}

				if (rm.getPosition(this).equals(colony.getPosition())) {
					if(curr.isPresent() && rm.getPosition(this).equals(curr.get().getDeliveryLocation()) && pm.containerContains(this, curr.get()) 
							&& pm.getVehicleState(this).equals(VehicleState.IDLE)){
						pm.deliver(this, curr.get(), time);
						curr = Optional.absent();
						Environment.notifyDelivery();
					}
					rest(time.getTimeLeft());
				}
			}		

			/*

			 */
		}
		else {	//hunt
			if (!curr.isPresent()) {

				//precheck: (alive) prey within half tick distance
				//if so: curr becomes the detected prey and we moveTo(curr)
				// if not: ask a gradient vector and move or moveTo
				FoodSource seen = Environment.getFoodFromVisual(this);
				curr = Optional.fromNullable((Parcel) seen);
			}

			if(!curr.isPresent()) {	//we dont see food
				Point gradientVector = MapUtil.rescale(Environment.getGradientField(this), 1);
				Point destinationPoint = MapUtil.addPoints(gradientVector, getPosition().get());
				destination = Optional.fromNullable(redirectInBounds(destinationPoint, rm));
				Point pos1 = getPosition().get();
				rm.moveTo(this, destination.get(), time);
				Point pos2 = getPosition().get();
				double distance = Point.distance(pos1,pos2);
				decreaseEnergy(distance);
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
					boolean dropping = false;
					if(getEnergy() <= 0){
						//curr = Optional.absent();
						Parcel it = curr.get();
						///Probably this is not the best way to do it. It should happen in the environment
						pm.drop(this, it, time); 
						List<Point> positions = new ArrayList<Point>();
						positions.add(0, this.getPosition().get());
						positions.add(1, it.getDeliveryLocation());

						Environment.dropFood((FoodElement) it, positions);
						//System.out.println("Drop food element");
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
					decreaseEnergy(distance);
					if (rm.equalPosition(this, curr.get())) {
						// pickup food element
						curr = Optional.fromNullable((Parcel) Environment.pickup((FoodSource) curr.get()));	// ugly
						decreaseEnergy(((FoodElement) curr.get()).getFixedCost());
						pm.pickup(this, curr.get(), time);
					}
				}
			}
		}
	}

	public boolean wantToRest(){
		return wantToRest;
	}

	private boolean wantToRest(boolean inCargo) {
		if(isResting())
			return true;
		boolean result = false;
		if(energyStrategy.bold == false && energyStrategy.dynamic == false){
			result = getEnergy() < getMaxEnergy()/10;
		}
		if(energyStrategy.bold == true && energyStrategy.dynamic == false){
			result = getEnergy() < getMaxEnergy()/20;
		}
		if(energyStrategy.bold == false && energyStrategy.dynamic == true){
			if(curr.isPresent()){
				if(!inCargo){
					double expectedCost = Point.distance(this.getPosition().get(), curr.get().getPickupLocation())
							+ Point.distance(curr.get().getDeliveryLocation(), curr.get().getPickupLocation()) * 2;  
					if(expectedCost > (getEnergy() + 1)){
						result = true;
					}
				}else{		//food in cargo: determine if we expect to need resting when we deliver
					if(getEnergy() < 3){
						result = true;
					}
				}
			}else {			//roaming the gradient field without parcel
				//In GF we cannot just calculate the nearest colony, the ant should be able to 'see' the colony an go back to it
				// so ant should just wander around until it sees a colony.
				if ( getEnergy() < getMaxEnergy()/10){
					result = true;
				}
			}
		}

		if(energyStrategy.bold == true && energyStrategy.dynamic == true){
			if(curr.isPresent()){
				if(!inCargo){
					double expectedCost = Point.distance(this.getPosition().get(), curr.get().getPickupLocation())
							+ Point.distance(curr.get().getDeliveryLocation(), curr.get().getPickupLocation()) * 2;
					if(expectedCost > (getEnergy() + 2)){
						result = true;
					}
				}else{		//food in cargo: determine if we expect to need resting when we deliver
					if(getEnergy() < 0){
						result = true;
					}
				}
			}else {			//roaming the gradient field without parcel
				/****************************************************************/
				//In GF we cannot just calculate the nearest colony, the ant should be able to 'see' the colony an go back to it
				// so ant should just wander around until it sees a colony. Without picking up a food element?
				if ( getEnergy() < getMaxEnergy()/25){ 
					// sizes of the map
					result = true;
				}
				
				if (Point.distance(this.getPosition().get(), Environment.getNearestColony(this).getPosition()) > getEnergy() + 0.2){ 
					result = true;
				}
				
			}
		}
		return result;
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

	public class Strategy {
		boolean bold;
		boolean dynamic;
		Strategy(boolean bold, boolean dynamic){
			this.bold = bold;
			this.dynamic = dynamic;
		}
	}
}
