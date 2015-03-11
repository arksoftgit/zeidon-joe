/**
    This file is part of the Zeidon Java Object Engine (Zeidon JOE).

    Zeidon JOE is free software: you can redistribute it and/or modify
    it under the terms of the GNU Lesser General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Zeidon JOE is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public License
    along with Zeidon JOE.  If not, see <http://www.gnu.org/licenses/>.

    Copyright 2009-2014 QuinSoft
 */

package com.quinsoft.zeidon.objectbrowser;

import java.awt.BorderLayout;
import java.awt.HeadlessException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;

/**
 * @author DG
 *
 */
class ViewChooser extends JPanel implements ActionListener
{
    private static final long serialVersionUID = 1L;
    private static final String REFRESH  = "Refresh";
    private static final String SAVE_ENV = "SaveEnv";

    private final BrowserEnvironment env;
    private final ViewListTable viewList;
    private final TaskList taskList;

    /**
     * @param objectEngine
     * @throws HeadlessException
     */
    ViewChooser(BrowserEnvironment environment )
    {
        super();
        this.env = environment;
        this.setName( "ViewChooser" );
        setLayout( new BorderLayout() );

        viewList = new ViewListTable( this.env );
        taskList = new TaskList( this.env );

        JScrollPane scrollableTasks = new JScrollPane( taskList );
        JScrollPane scrollableViews = new JScrollPane( viewList );

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, scrollableTasks, scrollableViews );
        splitPane.setName( "ViewChooserSplitPane" );
        splitPane.setResizeWeight( 0.4 );

        JPanel buttonPane = new JPanel();
        JButton refresh = addButton( buttonPane, "Refresh", REFRESH );
        refresh.setMnemonic( KeyEvent.VK_R );
        final JCheckBox showUnnamed = new JCheckBox( "Unnamed", env.isShowUnnamedViews() );
        showUnnamed.addItemListener( new ItemListener(){
            @Override
            public void itemStateChanged( ItemEvent e )
            {
                env.setShowUnnamedViews( showUnnamed.isSelected() );
                refresh();
            }} );
        buttonPane.add( showUnnamed );
        addButton( buttonPane, "Save Settings", SAVE_ENV );

        add( splitPane, BorderLayout.CENTER );
        add( buttonPane, BorderLayout.SOUTH );
    }

    private JButton addButton( JPanel buttonPane, String title, String command )
    {
        JButton button = new JButton( title );
        button.setActionCommand( command );
        button.addActionListener( this );
        buttonPane.add( button );
        return button;
    }

    void refresh()
    {
        taskList.refresh();
        viewList.refresh( taskList.getCurrentTask() );
    }

    @Override
    public void actionPerformed(ActionEvent action)
    {
        if ( action.getActionCommand().equals( REFRESH ) )
            refresh();
        else
        if ( action.getActionCommand().equals( SAVE_ENV ) )
            env.saveEnvironment();
    }
}