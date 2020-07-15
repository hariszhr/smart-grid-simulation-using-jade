import java.util.ArrayList;
import java.util.Date;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import jade.core.AID;

public class Records {
	
	private volatile static AtomicInteger _hour = new AtomicInteger();
	private volatile static AtomicInteger _day = new AtomicInteger(1);

//	Bills
	private volatile static ConcurrentHashMap<AID, Integer> _house_bills = new ConcurrentHashMap<AID, Integer>();
	
//	Entities
	private volatile static ConcurrentHashMap<AID, Integer> _houses = new ConcurrentHashMap<AID, Integer>();
	
	
	private volatile static ConcurrentHashMap<AID, Integer> _powerSuppliers_usage = new ConcurrentHashMap<AID, Integer>();
	private volatile static ConcurrentHashMap<AID, Integer> _powerSuppliers_capacity = new ConcurrentHashMap<AID, Integer>();
	private volatile static ConcurrentHashMap<AID, Integer> _powerSuppliers_rate = new ConcurrentHashMap<AID, Integer>();
	
	private volatile static ConcurrentHashMap<AID, Integer> _appliances_capacity = new ConcurrentHashMap<AID, Integer>();
	private volatile static ConcurrentHashMap<AID, Integer> _appliances_consumption = new ConcurrentHashMap<AID, Integer>();
	
//	subscriptions
	private volatile static ConcurrentHashMap<AID, AID> _subscription_of_houses = new ConcurrentHashMap<AID, AID>();
	
	private volatile static ConcurrentHashMap<AID, AID> _appliance_to_house_mapping = new ConcurrentHashMap<AID, AID>();
	
	private volatile static ConcurrentHashMap<AID, Long> _subscription_timestamp = new ConcurrentHashMap<AID, Long>();
	
	private volatile static ConcurrentHashMap<AID, Integer> _houses_on_electric_car_power = new ConcurrentHashMap<AID, Integer>();
	
	
//	TIMING
	public static synchronized AtomicInteger get_day() {
		return _day;
	}
	
	public static synchronized AtomicInteger get_hour() {
		if(_hour.get()==24) _hour.set(0);
		return _hour;
	}
	
	public static synchronized ConcurrentHashMap<AID, Integer> get_houses_on_electric_car_power() {
		return _houses_on_electric_car_power;
	}
	
//	------------- subscriptions --------------------------------------------------------------------------
	public static synchronized void registerHouseSubscription(AID house, AID powersupplier, long time) {
		_subscription_of_houses.put(house, powersupplier);
		_subscription_timestamp.put(house, time);
	}
	
	public static synchronized void removeHouseSubscription(AID house) {
		_subscription_of_houses.remove(house);
		_subscription_timestamp.remove(house);
	}
	
//	-----------------ADD agents---------------------------------------------------------------------
	
	
	public static synchronized void addHouse(AID house, int consumption) {
		_houses.put(house, consumption);
	}
	
	public static synchronized void removeHouse(AID house) {
		_houses.remove(house);
		_subscription_of_houses.remove(house);
		_subscription_timestamp.remove(house);
	}
	
	public static synchronized void addAppliance(AID applicance, String house_name, int consumption, int capacity) {
		_appliances_consumption.put(applicance, consumption);
		_appliances_capacity.put(applicance, capacity);
		
		for(AID e: _houses.keySet()) {
			if(e.getLocalName().equalsIgnoreCase(house_name)) {
				_appliance_to_house_mapping.put(applicance, e);
				break;
			}
		}
	}
	
	
	public static synchronized void removeAppliance(AID applicance) {
		_appliances_consumption.remove(applicance);
		_appliances_capacity.remove(applicance);
		_appliance_to_house_mapping.remove(applicance);
	}
	
	/**
	 * 
	 * @param supplier
	 * @param capacity
	 * @param rate
	 */
	public static synchronized void addPowerSupplier(AID supplier, int capacity, int price) {
		_powerSuppliers_rate.put(supplier, price);
		_powerSuppliers_capacity.put(supplier, capacity);
		_powerSuppliers_usage.put(supplier, 0);
	}
	
	public static synchronized void removePowerSupplier(AID supplier) {
		_powerSuppliers_rate.remove(supplier);
		_powerSuppliers_capacity.remove(supplier);
		_powerSuppliers_usage.remove(supplier);
		
		
		if(_subscription_of_houses.containsValue(supplier)) {
			
			
			ArrayList<AID> unsub_house_list = new ArrayList<AID>();
			for(Entry<AID, AID> e : _subscription_of_houses.entrySet()) {
				if(e.getValue().equals(supplier)) {
					unsub_house_list.add(e.getKey());
				}
			}
			
			for(AID e : unsub_house_list) {
				removeHouseSubscription(e);
			}
			
		}
		
		
		
	}	
	
//	---------- GETTERS ---------------------------------------------------------------------------------------------------------
	
	public static synchronized ConcurrentHashMap<AID, Integer> get_appliances_capacity() {
		return _appliances_capacity;
	}
	
	public static synchronized ConcurrentHashMap<AID, Integer> get_appliances_consumption() {
		return _appliances_consumption;
	}
	
	public static synchronized ConcurrentHashMap<AID, Integer> get_house_bills() {
		return _house_bills;
	}
	
	public static synchronized ConcurrentHashMap<AID, Integer> get_houses() {
		return _houses;
	}
	
	public static synchronized ConcurrentHashMap<AID, Integer> get_powerSuppliers_capacity() {
		return _powerSuppliers_capacity;
	}
	
	public static synchronized ConcurrentHashMap<AID, Integer> get_powerSuppliers_usage() {
		return _powerSuppliers_usage;
	}
	
	public static synchronized ConcurrentHashMap<AID, Integer> get_powerSuppliers_rate() {
		return _powerSuppliers_rate;
	}
	
	
	public static synchronized ConcurrentHashMap<AID, AID> get_subscription_of_houses() {
		return _subscription_of_houses;
	}
	
	public static synchronized ConcurrentHashMap<AID, AID> get_appliance_to_house_mapping() {
		return _appliance_to_house_mapping;
	}
	
	public static synchronized ConcurrentHashMap<AID, Long> get_subscription_timestamp() {
		return _subscription_timestamp;
	}
	
	public static synchronized AID getAID(String agent_localname){
		for(AID each: _houses.keySet()) {
			if(each.getLocalName().equalsIgnoreCase(agent_localname)) {
				return each;
			}
		}
		
		for(AID each: _powerSuppliers_capacity.keySet()) {
			if(each.getLocalName().equalsIgnoreCase(agent_localname)) {
				return each;
			}
		}
		
		for(AID each: _appliance_to_house_mapping.keySet()) {
			if(each.getLocalName().equalsIgnoreCase(agent_localname)) {
				return each;
			}
		}
		
		return null;
	}
	
//	------------ OTHERS -------------------------------------------------------------------------
	/**
	 * 
	 * @param agent all agent types
	 * @return
	 */
	public static synchronized boolean agentExists(AID agent) {
		return (_powerSuppliers_capacity.containsKey(agent) && _powerSuppliers_rate.containsKey(agent)) ||
				(_appliances_capacity.containsKey(agent) && _appliances_consumption.containsKey(agent)) ||
				_houses.containsKey(agent);
	}
	
	public static synchronized boolean houseHasSubscription(AID house) {
		return _subscription_of_houses.containsKey(house);
	}
	
	public static synchronized void incrementCurrentTime() {
		int hr = Records.get_hour().incrementAndGet();
		if(hr==24) {
			Records.get_hour().set(0);
			Records.get_day().incrementAndGet();
		}
	}
	
	
	/**
	 * In seconds
	 * @return
	 */
	public static synchronized long getCurrentTime() {
		
		return new Date().getTime()/1000;
	}

}
