package rojira.nysf2014;

import static rojira.jsi4.LibConsole.cdebug;
import static rojira.jsi4.LibConsole.cinfo;
import static rojira.jsi4.LibConsole.cverbose;
import static rojira.jsi4.LibGUI.Black;
import static rojira.jsi4.LibGUI.Grey;
import static rojira.jsi4.LibGUI.argbAd;
import static rojira.jsi4.LibGUI.argbBd;
import static rojira.jsi4.LibGUI.argbGd;
import static rojira.jsi4.LibGUI.argbRd;
import static rojira.jsi4.LibGUI.darken;
import static rojira.jsi4.LibGUI.rgb;
import static rojira.jsi4.LibIO.load_resource;
import static rojira.jsi4.LibText.fmt;
import static rojira.jsi4.LibText.str;
import static rojira.sigl.GLWrap.GL_COLOR_BUFFER_BIT;
import static rojira.sigl.GLWrap.GL_DEPTH_BUFFER_BIT;
import static rojira.sigl.GLWrap.GL_LINE_SMOOTH;
import static rojira.sigl.GLWrap.GL_LINE_STRIP;
import static rojira.sigl.GLWrap.glEnable;
import static rojira.sigl.GLWrap.glLineWidth;
import static rojira.sigl.LibSiGL.antialias;
import static rojira.sigl.LibSiGL.begin_shape;
import static rojira.sigl.LibSiGL.camera;
import static rojira.sigl.LibSiGL.clip;
import static rojira.sigl.LibSiGL.clw;
import static rojira.sigl.LibSiGL.colour;
import static rojira.sigl.LibSiGL.depth_test;
import static rojira.sigl.LibSiGL.end_shape;
import static rojira.sigl.LibSiGL.grid_XZ;
import static rojira.sigl.LibSiGL.modelview_matrix;
import static rojira.sigl.LibSiGL.perspective;
import static rojira.sigl.LibSiGL.pop_matrix;
import static rojira.sigl.LibSiGL.push_matrix;
import static rojira.sigl.LibSiGL.rotateX;
import static rojira.sigl.LibSiGL.textures;
import static rojira.sigl.LibSiGL.vertex;

import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.Arrays;

import rojira.jsi4.modules.eou.ConfigParser;
import rojira.jsi4.util.maths.Vec3d;
import rojira.jsi4.util.text.EString;
import rojira.siren.GLScene;
import rojira.siren.Siren;

/**
 * @author Rojira Projects
 * @since 2024-07-11
*/
public class NYSF2014 extends GLScene
{
	ConfigParser conf;

	Siren siren;

	double cam_dist;

	double cam_step;

	double cam_near;

	double cam_far;

	boolean draw_grid;

	char grid_key;

	double grid_min;

	double grid_max;

	double grid_size;

	int grid_lines;

	char grid_up_key;

	char grid_down_key;

	char grid_left_key;

	char grid_right_key;

	boolean draw_labels;

	double labels_size;

	double labels_dsize;

	char labels_key;

	char labels_inc_key;

	char labels_dec_key;

	int current_step;

	CelestialSystem[] systems;
	
	CelestialSystem system;

	boolean paused;
	
	public static void main( String[] args ) throws Throwable
	{
		cinfo.println( "NYSF2014 starting" );

		new NYSF2014();
	}


	public NYSF2014() throws Throwable
	{
		cinfo.println( "NYSF2014 default constructor" );

		init();

		siren = new Siren();

		siren.initialise_gl_window( 1024, 768 );

		siren.exit_on_close();

		siren.add_component( this );

		siren.start_rendering( 30 );

		//siren.start_rendering();
	}

	private void init() throws Throwable
	{
		//conf = ConfigParser.create( Main.config_opts.get_param() );
		
		conf = new ConfigParser().parse( new String( load_resource( this, "NYSF2014.conf" ) ) );

		cam_dist = conf.get_double( "camera/dist" );

		cam_step = conf.get_double( "camera/step" );

		cam_near = conf.get_double( "camera/near" );

		cam_far = conf.get_double( "camera/far" );

		draw_grid = true;

		grid_key = conf.get_value( "grid/key" ).charAt( 0 );

		grid_min = conf.get_double( "grid/min" );

		grid_max = conf.get_double( "grid/max" );

		grid_lines = conf.get_int( "grid/lines" );

		grid_size = ( grid_max - grid_min ) / grid_lines;

		grid_up_key = conf.get_value( "grid/up_key" ).charAt( 0 );

		grid_down_key = conf.get_value( "grid/down_key" ).charAt( 0 );

		grid_left_key = conf.get_value( "grid/left_key" ).charAt( 0 );

		grid_right_key = conf.get_value( "grid/right_key" ).charAt( 0 );

		draw_labels = true;

		labels_size = conf.get_double( "labels/size" );

		labels_dsize = conf.get_double( "labels/dsize" );

		labels_key = conf.get_value( "labels/key" ).charAt( 0 );

		labels_inc_key = (char) conf.get_int( "labels/inc_key_code" );

		labels_dec_key = (char) conf.get_int( "labels/dec_key_code" );
		
		//********* Parse systems
		
		String[] system_ids = conf.get_values( "systems" );

		systems = new CelestialSystem[ system_ids.length ];

		cdebug.println( "system_ids = %s", str( system_ids ) );

		int i = 0;

		for( String system_id : system_ids )
		{
			systems[ i ] = parse_system_conf( system_id );
			
			i ++;
		}
		
		system = systems[ 0 ];
		
		//********* End Parse systems
		
		paused = true;
	}


	private CelestialSystem parse_system_conf( String system_id ) throws Throwable
	{
		CelestialSystem system = new CelestialSystem();
		
		system.conf = new ConfigParser().parse( new String( load_resource( this, system_id + ".conf" ) ) );

		system.name = system.conf.get_value( "name" );

		system.G = system.conf.get_double( "Gravitational_Constant" );

		system.time_step = system.conf.get_double( "time_step" );
		
		system.trail_length = system.conf.get_int( "trail_length" );

		String[] object_names = system.conf.get_values( "objects" );

		system.objects = new CelestialObject[ object_names.length ];

		cdebug.println( "object_names = %s", str( object_names ) );

		int i = 0;

		for( String object_name : object_names )
		{
			system.objects[ i ] = parse_object_conf( system, object_name );

			system.objects[ i ].index = i;

			i++;
		}

		system.centered_object_index = system.conf.get_int( "centered_object_index" );

		if( system.centered_object_index < 0 ) system.centered_object_index = 0;

		if( system.centered_object_index >= system.objects.length ) system.centered_object_index = system.objects.length - 1;
		
		return system;
	}
	
	
	private CelestialObject parse_object_conf( CelestialSystem system, String object_name ) throws Throwable
	{
		cinfo.println( "Parsing conf values for: %s", object_name );

		CelestialObject co = new CelestialObject();

		co.name = object_name;

		co.mass = system.conf.get_double( object_name + "/mass" );

		co.radius = system.conf.get_double( object_name + "/radius" );

		co.ivel = Util._vec3d( system.conf.get_doubles( object_name + "/velocity" ) );
		
		co.vel = new Vec3d( co.ivel );
		
		co.ipos = Util._vec3d( system.conf.get_doubles( object_name + "/position" ) );
		
		co.pos = new Vec3d( co.ipos );
		
		co.col = Util._rgb( system.conf.get_ints( object_name + "/colour" ) );

		co.key = system.conf.get_value( object_name + "/key" ).charAt( 0 );

		co.positions = new Vec3d[ system.trail_length ];

		co.colours = new int[ system.trail_length ];

		for( int i=0; i<system.trail_length; i++ )
		{
			co.positions[ i ] = new Vec3d( co.pos );

			co.colours[ i ] = darken( co.col, (int) ( 0.5 * i ) );

			//co.colours[ i ] = co.col;
		}

		co.active = true;

		cinfo.println( co );

		return co;
	}


	public void initGL()
	{
		depth_test( false );

		//depth_write( true );

		antialias( true );
	}


	public void updateGL( double dt )
	{
		if( paused ) return;
		
		if( system == null ) return;
		
		system.frames ++;

		dt = system.time_step;

		Vec3d r = new Vec3d();

		Vec3d dv = new Vec3d();

		Vec3d ds = new Vec3d();

		for( CelestialObject co1 : system.objects )
		{
			if( co1.mass == 0 ) continue;

			co1.d_acc.zero();

			r.zero();

			dv.zero();

			ds.zero();

			for( CelestialObject co2 : system.objects )
			{
				if( co1 == co2 ) continue;

				if( co2.mass == 0 ) continue;

				r.set( co2.pos );

				r.sub( co1.pos );

				double dist = r.length();

				// a = GM/r^2

				double a  = ( system.G * co2.mass ) / ( dist * dist );

				// a in vector form

				co1.d_acc.x += a * r.x / dist;

				co1.d_acc.y += a * r.y / dist;

				co1.d_acc.z += a * r.z / dist;
			}

			dv.set( dt, co1.d_acc );

			ds.set( dt, co1.vel );

			ds.add( dt, dv );

			co1.vel.add( dv );

			co1.pos.add( ds );

			co1.positions[ system.frames % system.trail_length ].set( co1.pos );
		}

		//cout.println( "%dy %dd", frames/365, frames%365 );
	}

	public void displayGL()
	{
		/*
		if( current_step != 3650 )
		{
			current_step ++;

			return;
		}
		*/

		clip( 0, 0, siren.glwin.getWidth(), siren.glwin.getHeight() );

		clw( Black, GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT );

		perspective( 30, siren.glwin.getWidth() , siren.glwin.getHeight(), cam_near, cam_far );
		//perspective( 30, siren.glwin.getWidth() , siren.glwin.getHeight(), .01, 10000 );

		camera( 0, cam_dist, 0, 0, 0, 0, 0, 0, -1 );
		//camera( 0, cam_dist, cam_dist, 0, 0, 0, 0, 1, 0 );
		//camera( 0, 10, 10, 0, 0, 0, 0, 1, 0 );

		modelview_matrix();

		glEnable( GL_LINE_SMOOTH );

		if( draw_grid )
		{
			glLineWidth( 1.0f );

			colour( Grey );

			grid_XZ( grid_min, grid_max, 0, grid_lines );
		}


		if( system != null )
		{
			Vec3d v = new Vec3d();

			glLineWidth( 2.0f );

			for( CelestialObject co : system.objects )
			{
				if( co.mass == 0 ) continue;

				begin_shape( GL_LINE_STRIP );

				for( int i=0; i<system.trail_length; i++ )
				{
					//int pos_index = abs_mod( frames - i, trail_length );

					int pos_index = abs_mod( system.frames + i + 1, system.trail_length );
					
					colour( co.colours[ system.trail_length - i - 1 ] );
					
					//colour( co.colours[ pos_index ] );

					v.set( co.positions[ pos_index ] );

					v.sub( system.objects[ system.centered_object_index ].positions[ pos_index ] );

					vertex( v.x, v.y, v.z );
				}

				end_shape();

				if( draw_labels )
				{
					push_matrix();
					
					rotateX( -90 );

					textures( true );

					siren.text_renderer.begin3DRendering();

					siren.text_renderer.setSmoothing( true );

					siren.text_renderer.setColor( (float) argbRd( co.col ), (float) argbGd( co.col ), (float) argbBd( co.col ), (float) argbAd( co.col ) );

					int pos_index = abs_mod( system.frames, system.trail_length );

					v.set( co.positions[ pos_index ] );

					v.sub( system.objects[ system.centered_object_index ].positions[ pos_index ] );

					siren.text_renderer.draw3D( co.name, (float) v.x, (float) -v.z, (float) v.y, (float) labels_size );
					
					// siren.text_renderer.draw3D( co.name, (float) v.x, (float) v.y, (float) v.z, (float) labels_size );
					
					siren.text_renderer.flush();

					siren.text_renderer.end3DRendering();

					textures( false );
					
					pop_matrix();
				}

				siren.text_renderer.beginRendering( siren.glwin.getWidth(), siren.glwin.getHeight() );
				siren.text_renderer.setColor( 0.8f, 0.8f, 0.8f, 1.0f);
				// assuming dt = 1day (86400sec)
				siren.text_renderer.draw( fmt( "%02dy %03dd", system.frames/365, system.frames%365 ), 0, 10 );
				siren.text_renderer.endRendering();

			}
		}
		/*
		if( current_step == 3650 )
		{
			try
			{
				Thread.currentThread().yield();

				glFlush();

				BufferedImage image = back_buffer_to_image();

				// Flip the image vertically
				AffineTransform tx = AffineTransform.getScaleInstance(1, -1);
				tx.translate(0, -image.getHeight(null));
				AffineTransformOp op = new AffineTransformOp(tx, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
				image = op.filter(image, null);

				File tmp_image_file = new File( fmt( "img-%05d.tmp.png", current_step ) );

				save_image( image, tmp_image_file );

				File image_file = new File( fmt( "img-%05d.png", current_step ) );

				tmp_image_file.renameTo( image_file );
			}
			catch( Throwable error )
			{
				cerr.println( "Error writing image for step: %d", current_step );

				cerr.println( retrace( error ) );
			}

			siren.glwin.dispose();

			CelestialSystem.exit( 0 );
		}
		*/

		current_step ++;
	}


	public int abs_mod( int i, int mod )
	{
		i = i % mod;

		if( i < 0 ) i += mod;

		return i;
	}


/*
	public boolean inside_roche_limit( CelestialObject co1, CelestialObject co2, double dist )
	{
		if( c01.mass > c02.mass ) return false;

		double rd = 1.26 * c02.radius * pow( c01.mass / c02.mass, 1.0/3.0 );

		return rd < dist;
	}
*/


	public void key_pressed( char key, int code, KeyEvent e )
	{
		cinfo.println( "key_pressed: %s", e );
	}

	public void key_released( char key, int code, KeyEvent e )
	{
		cinfo.println( "key_released: %s", e );
		
		if( code >= 112 && code <= 112 + systems.length - 1 )
		{
			cdebug.println( "switching to system: %d", code - 112 );
			
			system = systems[ code - 112 ];
			
			paused = true;
			
			siren.glwin.setTitle( system.name );
		}
	}

	public void key_typed( char key, int code, KeyEvent e )
	{
		cinfo.println( "key_typed: %s", e );

		if( system != null )
		{
			if( key == 'r' )
			{
				system.reset();
				
				paused = true;

				return;
			}
			
			for( CelestialObject co : system.objects )
			{
				if( key == co.key )
				{
					system.centered_object_index = co.index;

					return;
				}
			}
			
			if( system.centered_object_index == 0 && key == grid_up_key )
			{
				system.objects[ 0 ].pos.z -= grid_size;

				recentre_grid();

				return;
			}

			if( system.centered_object_index == 0 && key == grid_down_key )
			{
				system.objects[ 0 ].pos.z += grid_size;

				recentre_grid();

				return;
			}

			if( system.centered_object_index == 0 && key == grid_left_key )
			{
				system.objects[ 0 ].pos.x -= grid_size;

				recentre_grid();

				return;
			}

			if( system.centered_object_index == 0 && key == grid_right_key )
			{
				system.objects[ 0 ].pos.x += grid_size;

				recentre_grid();

				return;
			}
		}

		if( key == grid_key )
		{
			draw_grid = ! draw_grid;

			return;
		}

		if( key == labels_key )
		{
			draw_labels = ! draw_labels;

			return;
		}

		if( key == labels_inc_key )
		{
			labels_size += labels_dsize;

			return;
		}

		if( key == labels_dec_key )
		{
			labels_size -= labels_dsize;

			return;
		}

		if( key == 'p' )
		{
			paused = ! paused;

			cdebug.println( "paused = %b", paused );
			
			return;
		}
	}

	void recentre_grid()
	{
		for( int i=0; i<system.trail_length; i++ )
		{
			system.objects[ 0 ].positions[ i ].set( system.objects[ 0 ].pos );
		}
	}

	public void mouse_clicked( MouseEvent e )
	{
	}

	public void mouse_dragged( MouseEvent e )
	{
	}

	public void mouse_entered( MouseEvent e )
	{
	}

	public void mouse_exited( MouseEvent e )
	{
	}

	public void mouse_moved( MouseEvent e )
	{
	}

	public void mouse_pressed( MouseEvent e )
	{
	}

	public void mouse_released( MouseEvent e )
	{
	}

	public void mouse_wheel_moved( MouseWheelEvent e )
	{
		cam_dist += e.getWheelRotation() * cam_step;
	}
}


class CelestialSystem
{
	String name;
	
	ConfigParser conf;
	
	CelestialObject[] objects;

	double G;

	int trail_length;

	int frames;

	int centered_object_index = 1;

	double time_step;
	
	void reset()
	{
		centered_object_index = 1;
		
		frames = 0;
		
		for( CelestialObject co : objects )
		{
			co.vel.set( co.ivel );
			
			co.pos.set( co.ipos );

			for( int i=0; i<trail_length; i++ )
			{
				co.positions[ i ].set( co.ipos );
			}
		}
	}
}


class CelestialObject
{
	String name;

	int index;

	double mass;

	double radius;

	Vec3d vel;
	
	Vec3d ivel;

	Vec3d pos;
	
	Vec3d ipos;

	Vec3d d_acc = new Vec3d();

	int col;

	char key;

	boolean active;

	Vec3d[] positions;

	int[] colours;

	public String toString()
	{
		try (EString es = new EString())
		{
			es.println( "name = %s", name );

			es.println( "mass = %f", mass );

			es.println( "radius = %f", radius );

			es.println( "vel = %s", vel );

			es.println( "pos = %s", pos );

			es.println( "col = %d", col );

			es.print( "key = %s", key );

			return es.toString();
		}
	}
}


class Util
{
	static Vec3d _vec3d( double[] xyz )
	{
		assert xyz != null;

		assert xyz.length == 3;

		return new Vec3d( xyz[ 0 ], xyz[ 1 ], xyz[ 2 ] );
	}

	static int _rgb( int[] rgb )
	{
		assert rgb != null;

		assert rgb.length == 3;

		cverbose.println( "parsing_colour: %s", Arrays.toString( rgb ) );

		return rgb( rgb[ 0 ], rgb[ 1 ], rgb[ 2 ] );
	}
}
