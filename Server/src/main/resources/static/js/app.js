App = Ember.Application.create();

App.Router.map(function() {
	this.route('login');
	this.route('signup');
});

App.IndexRoute = Ember.Route.extend({
	model : function() {
		return [ 'red', 'yellow', 'blue' ];
	},
	setupController : function(controller) {
		// `controller` is the instance of IndexController
		controller.set('title',
				"Roquito-Application platform for mobile developers");
	}
});

App.IndexController = Ember.Controller.extend({
	appName : 'Roquito'
});

App.User = Ember.Object.extend({
    userId : "",
    email : "",
    password : "",
    confirmPassword:"",
    fullName : "",
    company : "",
    profilePicUrl : "",
    
    save: function () {
    	this.confirmPassword = "";
        $.post({
          url: "/users",
          data: JSON.stringify( this.toArray() ),
          success: function ( data ) {
        	  alert(data)
            // your data should already be rendered with latest changes
            // however, you might want to change status from something to "saved" etc.
          }
        });
    }
});

App.SignupRoute = Ember.Route.extend({
	model: function(){
		return App.User.create()
	},
	
	setupController : function(controller, model){
		controller.set("user", model);
	}
});

App.SignupController = Ember.Controller.extend({
	actions : {
		submit : function() {
			var user = this.get("user");
			alert(user)
			if (user.password !== user.confirmPassword) {
				
			}
		},

		cancel : function() {
			this.transitionTo('index');
		}
	}
});
