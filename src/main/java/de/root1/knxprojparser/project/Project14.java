/*
 * Copyright (C) 2016 Alexander Christian <alex(at)root1.de>. All rights reserved.
 * 
 * This file is part of KnxProjParser.
 *
 *   KnxProjParser is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   KnxProjParser is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with KnxProjParser.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.root1.knxprojparser.project;

import de.root1.knxprojparser.Utils;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import org.knx.xml.project._14.Area;
import org.knx.xml.project._14.ComObject;
import org.knx.xml.project._14.ComObjectInstanceRef;
import org.knx.xml.project._14.ComObjectInstanceRefs;
import org.knx.xml.project._14.ComObjectRef;
import org.knx.xml.project._14.ComObjectRefs;
import org.knx.xml.project._14.Connectors;
import org.knx.xml.project._14.DeviceInstance;
import org.knx.xml.project._14.GroupAddress;
import org.knx.xml.project._14.GroupAddressReference;
import org.knx.xml.project._14.GroupAddresses;
import org.knx.xml.project._14.GroupRange;
import org.knx.xml.project._14.Installation;
import org.knx.xml.project._14.KNX;
import org.knx.xml.project._14.Line;
import org.knx.xml.project._14.Project;
import org.knx.xml.project._14.ProjectInformation;
import org.knx.xml.project._14.Static;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

/**
 *
 * @author achristian
 */
public class Project14 extends AbstractKnxParser<KNX> {

    private final Logger log = LoggerFactory.getLogger(Project14.class);

    public Project14(File baseFolder) throws SAXException {
        super("/xsd/project/Project_14.xsd", baseFolder);

    }

    @Override
    public void parse() throws ParseException {
        /**
         * P-0B09-0_GA-6 -> GroupAddressContainer
         */
        Map<String, GroupAddressContainer> gaId_to_ga_map = new HashMap<>();

        /**
         * GroupAddressContainer -> M-0083_A-0026-14-05BA_O-0_R-11026
         */
        Map<GroupAddressContainer, String> ga_to_comObjInstanceRefId_map = new HashMap<>();

        /**
         * {ComObjectRef.Id} -> DPT String
         * <br>
         * M-0083_A-0026-14-05BA_O-0_R-11026 -> 1.001
         */
        Map<String, String> comObjRef_to_dpt_map = new HashMap<>();
        
        
        /**
         * P-0B09-0_GA-6 -> "1.001"
         */
        Map<String, String> gaId_to_dpt_map = new HashMap<>();


        File[] projectFolders = baseFolder.listFiles(new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                return pathname.getName().startsWith("P-") && pathname.isDirectory();
            }

        });
        if (projectFolders.length != 1) {
            log.error("Can only handle 1 project in knxproj file. Found {}", projectFolders.length);
            for (File projectFolder : projectFolders) {
                log.error("Project folder: {}", projectFolder.getAbsolutePath());
            }
            System.exit(1);
        }

        File projectFolder = projectFolders[0];

        File projectFile = new File(projectFolder, "project.xml");

        try {
            KNX projectXML = readXML(projectFile, KNX.class);
            log.debug("CreatedBy={}", projectXML.getCreatedBy());
            log.debug("ToolVersion={}", projectXML.getToolVersion());

            project.setCreatedBy(projectXML.getCreatedBy());
            project.setToolVersion(projectXML.getToolVersion());

            Project project = projectXML.getProject();
            ProjectInformation projectInformation = project.getProjectInformation();
            log.debug("Name={}", projectInformation.getName());

            if (projectInformation.getLastModified() != null) {
                log.debug("LastModified={}", projectInformation.getLastModified().toGregorianCalendar().getTime());
                this.project.setLastModified(projectInformation.getLastModified().toGregorianCalendar().getTime());
            }

            this.project.setName(projectInformation.getName());
            if (projectInformation.getProjectStart() != null) {
                this.project.setProjectStart(projectInformation.getProjectStart().toGregorianCalendar().getTime());
            }

            File idProjectFile = new File(projectFolder, "0.xml");

            KNX idProjectXML = readXML(idProjectFile, KNX.class);
            Installation installation = idProjectXML.getProject().getInstallations().getInstallation();
            log.debug("getBCUKey={}", installation.getBCUKey());

            GroupAddresses groupAddresses = installation.getGroupAddresses();
            List<GroupRange> level1Ranges = groupAddresses.getGroupRanges().getGroupRange();
            for (GroupRange level1Range : level1Ranges) {
                List<GroupRange> level2Range = (List<GroupRange>) (Object) level1Range.getGroupRangeOrGroupAddress();

                for (GroupRange groupRange : level2Range) {
                    List<GroupAddress> groupAddressesList = (List<GroupAddress>) (Object) groupRange.getGroupRangeOrGroupAddress();

                    for (GroupAddress groupAddress : groupAddressesList) {
                        String id = groupAddress.getId();
                        int intAddr = groupAddress.getAddress();
                        int[] ga = Utils.convertGroupAddress(intAddr);
                        String strAddr = ga[0] + "/" + ga[1] + "/" + ga[2];
                        String name = groupAddress.getName();
                        log.debug("GA id={} ga={} name={}", id, strAddr, name);
                        gaId_to_ga_map.put(id, new GroupAddressContainer(strAddr, name, id));
                        
                        // check if DPT is already known on GA itself
                        String dptString = groupAddress.getDatapointType();
                        if (dptString!=null && !dptString.isEmpty()) {
                            String dpt = Utils.convertDpt(dptString);
                            gaId_to_dpt_map.put(id, dpt);
                        }
                    }

                }
            }

            List<Area> areas = installation.getTopology().getArea();
            for (Area area : areas) {
                List<Line> areaLines = area.getLine();
                for (Line areaLine : areaLines) {
                    List<DeviceInstance> deviceInstances = areaLine.getDeviceInstance();
                    for (DeviceInstance deviceInstance : deviceInstances) {
                        ComObjectInstanceRefs comObjectInstanceRefs = deviceInstance.getComObjectInstanceRefs();
                        if (comObjectInstanceRefs != null) {
                            comObjectInstanceRefs.getComObjectInstanceRef();
                            List<ComObjectInstanceRef> comObjectInstanceRefList = comObjectInstanceRefs.getComObjectInstanceRef();
                            for (ComObjectInstanceRef comObjectInstanceRef : comObjectInstanceRefList) {
                                String comObjInstanceRefId = comObjectInstanceRef.getRefId();
                                
                                String dptString = comObjectInstanceRef.getDatapointType();
                                
                                Connectors connectors = comObjectInstanceRef.getConnectors();
                                if (connectors != null) {
                                    List<JAXBElement<GroupAddressReference>> sendOrReceive = connectors.getSendOrReceive();
                                    
                                    for (JAXBElement<GroupAddressReference> ref : sendOrReceive) {

                                        String groupAddressRefId = ref.getValue().getGroupAddressRefId();
                                        GroupAddressContainer gac = gaId_to_ga_map.get(groupAddressRefId);

                                        log.debug("ComObj {} is connected to {}", comObjInstanceRefId, gac.getGa());
                                        ga_to_comObjInstanceRefId_map.put(gac, comObjInstanceRefId);
                                        
                                        if(dptString!=null && !dptString.isEmpty()) {
                                            String dpt = Utils.convertDpt(dptString);
                                            gaId_to_dpt_map.put(gac.getRefId(), dpt);
                                        }
                                        
                                    }
                                }

                            }
                        }
                    }
                }
            }

            // obtain folders containing manufacturer files
            File[] manufacturerFolders = baseFolder.listFiles(new FileFilter() {
                @Override
                public boolean accept(File f) {
                    return f.isDirectory() && f.getName().startsWith("M-");
                }
            });

            // for each found manufacturer folder
            for (File manufacturerFolder : manufacturerFolders) {

                // obtain manufacturer files
                String manufacturerFilePrefix = manufacturerFolder.getName();

                log.debug("Found manufacturer {}", manufacturerFilePrefix);

                File[] manufacturerFiles = manufacturerFolder.listFiles(new FileFilter() {
                    @Override
                    public boolean accept(File f) {
                        return f.isFile() && f.getName().startsWith(manufacturerFilePrefix);
                    }
                });

                // for each manufacturer file
                for (File manufacturerFile : manufacturerFiles) {
                    log.debug("Parsing {}", manufacturerFile);
                    KNX manufacturerXml = readXML(manufacturerFile, KNX.class);

                    Static aStatic = manufacturerXml.getManufacturerData().getManufacturer().getApplicationPrograms().getApplicationProgram().getStatic();

                    // read <ComObject> into localmap
                    Map<String, ComObject> comObjId_to_comObj_map = new HashMap<>();
                    if (aStatic.getComObjectTable() != null) {
                        List<ComObject> comObjectList = aStatic.getComObjectTable().getComObject();
                        for (ComObject comObject : comObjectList) {
                            comObjId_to_comObj_map.put(comObject.getId(), comObject);
                            log.debug("Found ComObject id={}", comObject.getId());
                        }
                    }

                    ComObjectRefs comObjectRefs = aStatic.getComObjectRefs();
                    if (comObjectRefs != null) {
                        List<ComObjectRef> comObjectRefList = comObjectRefs.getComObjectRef();
                        for (ComObjectRef comObjectRef : comObjectRefList) {
                            String id = comObjectRef.getId();
                            String refId = comObjectRef.getRefId();
                            String dpt = comObjectRef.getDatapointType();

                            if (dpt != null && !dpt.isEmpty()) {

                                String convertedDpt = Utils.convertDpt(dpt);
                                log.debug("ComObjectRef {} has DPT {}", id, convertedDpt);
                                comObjRef_to_dpt_map.put(id, convertedDpt);

                            } else {

                                // get DPT from <ComObj> 
                                // get comobj form map and query DPT
                                ComObject comObj = comObjId_to_comObj_map.get(refId);
                                String convertedDpt = Utils.convertDpt(comObj.getDatapointType());

                                log.debug("ComObjectRef {} has no DPT. But related ComObject has DPT {}", id, convertedDpt);

                                comObjRef_to_dpt_map.put(id, convertedDpt);

                            }
                        }
                    }
                }

            }

            Collection<GroupAddressContainer> gac = gaId_to_ga_map.values();
            for (GroupAddressContainer groupAddressContainer : gac) {
                String comObjectInstanceRefId = ga_to_comObjInstanceRefId_map.get(groupAddressContainer);
                String dpt;
                if (gaId_to_dpt_map.containsKey(groupAddressContainer.getRefId())) {
                    dpt = gaId_to_dpt_map.get(groupAddressContainer.getRefId());
                } else {
                    dpt = comObjRef_to_dpt_map.get(comObjectInstanceRefId);
                }
                de.root1.knxprojparser.GroupAddress ga = new de.root1.knxprojparser.GroupAddress(groupAddressContainer.getGa(), groupAddressContainer.getName(), dpt);
                gaList.add(ga);
            }

            this.project.setGroupaddressList(gaList);
            parsed = true;

        } catch (JAXBException | SAXException ex) {
            throw new ParseException("Error parsing", ex);
        }
    }

    @Override
    public boolean parserMatch() {
        File knxMaster = new File(baseFolder, "knx_master.xml");
        try {
            BufferedReader br = new BufferedReader(new FileReader(knxMaster));
            br.readLine();
            String line = br.readLine();
            if (line.contains("http://knx.org/xml/project/14")) {
                return true;
            }

        } catch (FileNotFoundException ex) {
        } catch (IOException ex) {
        }
        log.debug("does not match");
        return false;
    }

}
