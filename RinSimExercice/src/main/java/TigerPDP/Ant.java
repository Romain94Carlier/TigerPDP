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

	public static final double VISUAL_RANGE = 1d;	//debug
	private static final double SPEED = 1000d;
	private Optional<Parcel> curr;

	Optional<CommDevice> device;
	Optional<Point> destination;

	Ant(Point startPosition, int capacity) {
		super(VehicleDTO.builder()
				.capacity(capacity)
				.startPosition(startPosition)
				.speed(SPEED)
				.build());
		curr = Optional.absent();
		this.getClass().getResourceAsStream("/src/main/resources/69_tiger.jpg");

	}

	@Override
	public void afterTick(TimeLapse timeLapse) {}

	//strategy for gas refilling and passenger acceptance

	@Override
	protected void tickImpl(TimeLapse time) {
		final RoadModel rm = getRoadModel();
		final PDPModel pm = getPDPModel();

		//executeCentralizedTick(time, rm, pm);
		executeGradientFieldTick(time, rm, pm);
	}

	private void executeCentralizedTick(TimeLapse time, final RoadModel rm,
			final PDPModel pm) {
		if (!time.hasTimeLeft()) {
			return;
		}		//first check energy before hunting
		if (!curr.isPresent()) {
			//curr = Optional.fromNullable(RoadModels.findClosestObject(
			//rm.getPosition(this), rm, Parcel.class));
			//precheck: (alive) prey within half tick distance
			//if so: curr becomes the detected prey and we moveTo(curr)
			// if not: ask a gradient vector and move or moveTo
			curr = Optional.fromNullable(TaxiBase.getAssignment(this));		//adapt for better refresh			
		}

		if (curr.isPresent()) {
			final boolean inCargo = pm.containerContains(this, curr.get());
			// sanity check: if it is not in our cargo AND it is also not on the
			// RoadModel, we cannot go to curr anymore.
			// TODO: remove from taxibase?
			if (!inCargo && !rm.containsObject(curr.get())) {
				curr = Optional.absent();
			} else if (inCargo) {
				// if it is in cargo, go to its destination
				rm.moveTo(this, curr.get().getDeliveryLocation(), time);
				if (rm.getPosition(this).equals(curr.get().getDeliveryLocation())) {
					// deliver when we arrive
					pm.deliver(this, curr.get(), time);

				}
			} else {
				// it is still available, go there as fast as possible
				rm.moveTo(this, curr.get(), time);
				if (rm.equalPosition(this, curr.get())) {
					// pickup customer
					pm.pickup(this, curr.get(), time);
					TaxiBase.remove(curr.get());
				}
			}
		}
	}

	private void executeGradientFieldTick(TimeLapse time, final RoadModel rm,
			final PDPModel pm) {
		destination = Optional.absent();
		if (!time.hasTimeLeft()) {
			return;
		}		//first check energy before hunting
		if (!curr.isPresent()) {
			//curr = Optional.fromNullable(RoadModels.findClosestObject(
			//rm.getPosition(this), rm, Parcel.class));
			//precheck: (alive) prey within half tick distance
			//if so: curr becomes the detected prey and we moveTo(curr)
			// if not: ask a gradient vector and move or moveTo
			curr = Optional.fromNullable(GradientField.getFoodFromVisual(this));
		}

		if(!curr.isPresent()) {	//we dont see food
			Point gradientVector = MapUtil.rescale(GradientField.getGradientField(this), 1);
			Point destinationPoint = MapUtil.addPoints(gradientVector, getPosition().get());
			destination = Optional.fromNullable(redirectInBounds(destinationPoint, rm));
			rm.moveTo(this, destination.get(), time);
		}
		if (curr.isPresent()) {
			final boolean inCargo = pm.containerContains(this, curr.get());
			// sanity check: if it is not in our cargo AND it is also not on the
			// RoadModel, we cannot go to curr anymore.
			// TODO: remove from taxibase?
			if (!inCargo && !rm.containsObject(curr.get())) { //sanity/consistency check
				curr = Optional.absent();
			} else if (inCargo) {
				// if it is in cargo, go to its destination
				rm.moveTo(this, curr.get().getDeliveryLocation(), time);
				if (rm.getPosition(this).equals(curr.get().getDeliveryLocation())) {
					// deliver when we arrive
					pm.deliver(this, curr.get(), time);
					GradientField.notifyDelivery();
				}
			} else {
				// it is still available, go there as fast as possible
				rm.moveTo(this, curr.get(), time);
				if (rm.equalPosition(this, curr.get())) {
					// pickup customer
					curr = Optional.fromNullable(GradientField.pickup((FoodSource) curr.get()));	// ugly
					pm.pickup(this, curr.get(), time);
					
				}
			}
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
}
