import java.awt.Component;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.URL;
import java.util.HashMap;
import java.util.Map.Entry;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeCellRenderer;

import org.apache.log4j.Logger;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.FIPAException;

@SuppressWarnings("serial")
public class Stats extends Agent{
	
	private static Logger _logger = Logger.getLogger(Stats.class.getName());
	
	private static ImageIcon pstationI;
    private static ImageIcon houseI;
    private static ImageIcon applianceI;
    private static ImageIcon concordiaUniversityI;
    String r1="powerstation.jpg";
    String r2="house.jpg";
    String r3="appliance.jpg";
    String r4="ConcordiaUniversity.png";
    
    
    
    
	
	@Override
	protected void setup() {
		try {
			Registration.register(getAID(), AgentType.Stats.toString(), getLocalName(), this);
			
			
			pstationI = new ImageIcon(getResourceURL(r1));
		    houseI = new ImageIcon(getResourceURL(r2));
		    applianceI = new ImageIcon(getResourceURL(r3));
		    concordiaUniversityI = new ImageIcon(getResourceURL(r4));
			
			
			SwingUtilities.invokeLater(new Runnable() {
				
				@Override
				public void run() {
					JFrame treeFrame = new JFrame("JTREE");
					treeFrame.setSize(660, 1000);
					treeFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
					treeFrame.setVisible(true);
					JPanel jp=new JPanel();;
					treeFrame.add(jp);
					
					Image img = pstationI.getImage();
					Image newimg = img.getScaledInstance(30, 30,  java.awt.Image.SCALE_SMOOTH);
					pstationI = new ImageIcon(newimg);
					
					Image img2 = houseI.getImage();
					Image newimg2 = img2.getScaledInstance(30, 30,  java.awt.Image.SCALE_SMOOTH);
					houseI = new ImageIcon(newimg2);
					
					Image img3 = applianceI.getImage();
					Image newimg3 = img3.getScaledInstance(30, 30,  java.awt.Image.SCALE_SMOOTH);
					applianceI = new ImageIcon(newimg3);
					
					Image img4 = concordiaUniversityI.getImage();
					Image newimg4 = img4.getScaledInstance(30, 30,  java.awt.Image.SCALE_SMOOTH);
					concordiaUniversityI = new ImageIcon(newimg4);
					
					
					
					DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode("INSE6640_SMARTGRID");
					
					final JTree jt = new JTree(rootNode);
					jt.setBounds(10, 11, 640, 980);
			        jt.setCellRenderer(new MyCustomTreeCellRenderer());
			        JScrollPane pane = new JScrollPane(jt);
			        pane.setPreferredSize(new Dimension(650, 990));
					jp.add(pane);
					
					treeFrame.repaint();
					treeFrame.revalidate();
					
					
					new Timer(5000, new ActionListener() {
						
						
						
						
						@Override
						public void actionPerformed(ActionEvent e) {
							
							DefaultTreeModel model = (DefaultTreeModel)jt.getModel();
							DefaultMutableTreeNode rootNode = (DefaultMutableTreeNode)model.getRoot();
							
							rootNode.removeAllChildren();
							
							DefaultMutableTreeNode emptyNode = new DefaultMutableTreeNode("INACTIVE");
							
							DefaultMutableTreeNode timeNode = new DefaultMutableTreeNode("Time [DAY "+Records.get_day().get()+"] [HOUR "+Records.get_hour().get()+"]");
							
							rootNode.add(emptyNode);
							rootNode.add(timeNode);
							
							HashMap<String, DefaultMutableTreeNode> supplier_nodes = new HashMap<String, DefaultMutableTreeNode>();
							HashMap<String, DefaultMutableTreeNode> house_nodes = new HashMap<String, DefaultMutableTreeNode>();
							HashMap<String, DefaultMutableTreeNode> appliance_nodes = new HashMap<String, DefaultMutableTreeNode>();
							
							
							_logger.debug("house subscriptions");
							Records.get_subscription_of_houses().forEach((a,b)->{
								_logger.debug(a.getLocalName() +" -- "+b.getLocalName());
							});
							_logger.debug("----------------------");
							
//							add supplier nodes
							for(AID each : Records.get_subscription_of_houses().values()) {
								if(!supplier_nodes.containsKey(each.getLocalName())) {
									String supplierNAME = each.getLocalName();
									StringBuilder supplier_node_string = new StringBuilder(supplierNAME);
									supplier_node_string.append(" - ").append("( supply ").append(Records.get_powerSuppliers_capacity().get(each)).append(" Wh").append(")");
									supplier_node_string.append(" - ").append("( price ").append(Records.get_powerSuppliers_rate().get(each)).append(" $/Wh").append(")");
									DefaultMutableTreeNode supplierNode = new DefaultMutableTreeNode(supplier_node_string.toString());
									supplier_nodes.put(supplierNAME, supplierNode);
									rootNode.add(supplierNode);
								}
							}
							

							
//							add house nodes
							for(Entry<AID, AID> each : Records.get_subscription_of_houses().entrySet()) {
								
								DefaultMutableTreeNode supplierNode = supplier_nodes.get(each.getValue().getLocalName());
								
								if(supplierNode!=null) {
									String houseNAME = each.getKey().getLocalName();
									StringBuilder house_node_string = new StringBuilder(houseNAME);
									house_node_string.append(" - ").append("( use ").append(Records.get_houses().get(each.getKey())).append(" Wh").append(")");
									house_node_string.append("( bill $").append(Records.get_house_bills().get(each.getKey())).append(")");
									DefaultMutableTreeNode houseNode = new DefaultMutableTreeNode(house_node_string.toString());
									house_nodes.put(houseNAME, houseNode);
									
									supplierNode.add(houseNode);
								}
								
							}
							
//							add appliance nodes
							for(Entry<AID, AID> each: Records.get_appliance_to_house_mapping().entrySet()) {
								
								DefaultMutableTreeNode houseNode = house_nodes.get(each.getValue().getLocalName());
								
								if(houseNode!=null) {
									String applianceNAME = each.getKey().getLocalName();

									StringBuilder appliance_node_string;
									
									if(applianceNAME.endsWith(ApplianceTypes.airconditioner.toString()) && ApplianceSchedule.airconditioner[Records.get_hour().get()]!=-1) {
										appliance_node_string = new StringBuilder("*"+applianceNAME);
									} else if(applianceNAME.endsWith(ApplianceTypes.car.toString()) && ApplianceSchedule.car[Records.get_hour().get()]!=-1) {
										appliance_node_string = new StringBuilder("*"+applianceNAME);
									} else if(applianceNAME.endsWith(ApplianceTypes.lights.toString()) && ApplianceSchedule.lights[Records.get_hour().get()]!=-1) {
										appliance_node_string = new StringBuilder("*"+applianceNAME);
									} else {
										appliance_node_string = new StringBuilder(applianceNAME);
									}
									appliance_node_string.append(" - ").append("( use ").append(Records.get_appliances_consumption().get(each.getKey())).append(" Wh").append(")");
									if(each.getKey().getLocalName().contains("car")){
										appliance_node_string.append("( store ").append(Records.get_appliances_capacity().get(each.getKey())).append(" W").append(")");
									}
									DefaultMutableTreeNode applianceNode = new DefaultMutableTreeNode(appliance_node_string);
									appliance_nodes.put(applianceNAME, applianceNode);
									
									houseNode.add(applianceNode);
								}
							}
							
							
							for(Entry<AID, Integer> each : Records.get_powerSuppliers_capacity().entrySet()) {
								if(!supplier_nodes.containsKey(each.getKey().getLocalName())) {
									String supplierNAME = each.getKey().getLocalName();
									StringBuilder supplier_node_string = new StringBuilder(supplierNAME);
									supplier_node_string.append(" - ").append("( supply ").append(Records.get_powerSuppliers_capacity().get(each.getKey())).append(" Wh").append(")");
									supplier_node_string.append(" - ").append("( price ").append(Records.get_powerSuppliers_rate().get(each.getKey())).append(" $/Wh").append(")");
									DefaultMutableTreeNode supplierNode = new DefaultMutableTreeNode(supplier_node_string.toString());
									emptyNode.add(supplierNode);
								}
								
							}
							
							for(Entry<AID, Integer> each : Records.get_houses().entrySet()) {
								if(!house_nodes.containsKey(each.getKey().getLocalName())) {
									String houseNAME = each.getKey().getLocalName();
									StringBuilder house_node_string = new StringBuilder(houseNAME);
									house_node_string.append(" - ").append("( use ").append(Records.get_houses().get(each.getKey())).append(" Wh").append(")");
									DefaultMutableTreeNode houseNode = new DefaultMutableTreeNode(house_node_string.toString());
									emptyNode.add(houseNode);
								}
								
							}
							
							for(Entry<AID, Integer> each : Records.get_appliances_consumption().entrySet()) {
								if(!appliance_nodes.containsKey(each.getKey().getLocalName())) {
									String applianceNAME = each.getKey().getLocalName();
									StringBuilder appliance_node_string = new StringBuilder(applianceNAME);
									appliance_node_string.append(" - ").append("( use ").append(Records.get_appliances_consumption().get(each.getKey())).append(" Wh").append(")");
									if(each.getKey().getLocalName().contains("car")){
										appliance_node_string.append("( store ").append(Records.get_appliances_capacity().get(each.getKey())).append(" W").append(")");
									}
									DefaultMutableTreeNode applianceNode = new DefaultMutableTreeNode(appliance_node_string);
									emptyNode.add(applianceNode);
								}
								
							}
							
							for(AID each : Records.get_houses_on_electric_car_power().keySet()) {
								if(!supplier_nodes.containsKey(each.getLocalName())) {
									String batteryName = each.getLocalName();
									StringBuilder batteryNode = new StringBuilder(batteryName);
									batteryNode.append(" - is using electric car battery");
									DefaultMutableTreeNode batteryNode2 = new DefaultMutableTreeNode(batteryNode.toString());
									supplier_nodes.put(batteryName, batteryNode2);
									rootNode.add(batteryNode2);
								}
							}
							
							model.reload();
							
							for (int i = 0; i < jt.getRowCount(); i++) {
								jt.expandRow(i);
								}
							
							_logger.debug("---> FRAME width ["+treeFrame.getWidth()+"] height ["+treeFrame.getHeight()+"]");
							
						}
					}).start();	
				}
			});
			
			
		} catch (FIPAException e1) {
			e1.printStackTrace();
		}
		
//      A Tick behaviour to keep agent record up to date, by reading the DFService
		addBehaviour(new TickerBehaviour(this, 5000) {
			
			@Override
			protected void onTick() {
				Records.incrementCurrentTime();
				System.out.println("[CLOCK] [DAY] "+Records.get_day().get()+" [HOUR] "+Records.get_hour().get());
				
				
			}
		});
	}
	
	
	class MyCustomTreeCellRenderer implements TreeCellRenderer {

		JLabel label=null;
		
		public MyCustomTreeCellRenderer() {
			label=new JLabel(); 
		}
		
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded,
                                                      boolean leaf, int row, boolean hasFocus) {
            Object o = ((DefaultMutableTreeNode) value).getUserObject();
            if(o.toString().toLowerCase().contains("Power".toLowerCase())){
            	label.setIcon(pstationI);
            	label.setText("" + value);
            } else if(o.toString().toLowerCase().contains("house")){
            	label.setIcon(houseI);
            	label.setText("" + value);
            } else if(o.toString().contains(ApplianceTypes.airconditioner.toString()) || o.toString().contains(ApplianceTypes.car.toString()) || (o.toString().contains(ApplianceTypes.lights.toString()))){
            	label.setIcon(applianceI);
            	label.setText("" + value);
            } else {
            	label.setIcon(concordiaUniversityI);
            	label.setText("" + value);
            }
            return label;
        }
    }
	
	public static URL getResourceURL(String resource){
		if(isJar()){
			
			URL url = Stats.class.getResource("/resources/"+resource);
			if(url == null){
				url = Stats.class.getResource("/"+resource);
			}
			return url;
		} else {
			return ClassLoader.getSystemResource(resource);
		}
		
	}
    
    public static boolean isJar() {
		return Stats.class.getProtectionDomain().getCodeSource().getLocation().toString().endsWith(".jar");
	}
	
}